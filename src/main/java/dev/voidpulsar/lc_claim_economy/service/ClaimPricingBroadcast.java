package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.compat.ModCompat;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.network.PricingBroadcastPayload;
import dev.voidpulsar.lc_claim_economy.opc.OpcDashboardSync;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Note: this class's own method signatures/fields must never reference any
 * {@code dev.ftb.mods.*} or {@code xaero.pac.*} type - both are optional
 * dependencies (see {@code ModCompat}), and this class is loaded
 * unconditionally regardless of which claim mod (if either) is installed.
 * The actual FTB/OP&C-specific lookups below are guarded behind
 * {@code ModCompat} checks before the corresponding type is ever touched.
 */
public final class ClaimPricingBroadcast {
    private ClaimPricingBroadcast() {
    }

    public static void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, createPayload(player));
    }

    public static PricingBroadcastPayload createPayload(ServerPlayer player) {
        boolean balanceSynced = false;
        boolean balanceEmpty = true;
        String balanceText = "";
        int claimedChunks = 0;

        if (ModCompat.isFtbAvailable() && FTBTeamsAPI.api().isManagerLoaded()) {
            Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
            if (team != null) {
                BankLedgerAccess.ensurePartyAccountExists(player.server, team);
                IBankAccount account = BankLedgerAccess.getAccountForPlayer(player.server, player);
                balanceSynced = true;
                balanceEmpty = account.getMoneyStorage().isEmpty();
                if (!balanceEmpty) {
                    String text = account.getMoneyStorage().getAllValueText().getString();
                    // Defensive cap: FriendlyByteBuf#writeUtf throws an EncoderException
                    // (which disconnects the player) if the string exceeds its max length.
                    // A normal formatted balance is at most a few dozen characters, so this
                    // never triggers in practice, but it stops a malformed/huge value from
                    // ever taking the connection down.
                    if (text.length() > 256) {
                        text = text.substring(0, 256);
                    }
                    balanceText = text;
                }
                if (FTBChunksAPI.api().isManagerLoaded()) {
                    claimedChunks = FTBChunksAPI.api().getManager().getOrCreateData(team).getClaimedChunks().size();
                }
            }
        } else if (ModCompat.isOpcAvailable()) {
            OpcDashboardSync.Balance balance = OpcDashboardSync.resolve(player);
            balanceSynced = balance.synced();
            balanceEmpty = balance.empty();
            balanceText = balance.text();
            claimedChunks = balance.claimedChunks();
        }

        return new PricingBroadcastPayload(
                LcClaimEconomyConfig.SERVER.claimPrice.get(),
                LcClaimEconomyConfig.SERVER.forceLoadUpkeepPrice.get(),
                LcClaimEconomyConfig.SERVER.upkeepPeriodMinutes.get(),
                LcClaimEconomyConfig.SERVER.freeChunks.get(),
                claimedChunks,
                balanceSynced,
                balanceEmpty,
                balanceText,
                LcClaimEconomyConfig.SERVER.mobGriefProtectionPrice.get(),
                LcClaimEconomyConfig.SERVER.explosionProtectionPrice.get(),
                LcClaimEconomyConfig.SERVER.pvpDisablePrice.get(),
                LcClaimEconomyConfig.SERVER.blockInteractProtectionPrice.get(),
                LcClaimEconomyConfig.SERVER.blockEditProtectionPrice.get(),
                LcClaimEconomyConfig.SERVER.entityInteractProtectionPrice.get(),
                SafeguardPricing.landChunkGroupSize(),
                ModCompat.isFtbAvailable() && ConflictService.isEnabled()
        );
    }
}
