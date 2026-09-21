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
 * Pushes the client-facing pricing/balance snapshot used by the claim HUD. This class
 * loads unconditionally regardless of which claim mod (if any) is present, so its own
 * public signatures must stay free of {@code dev.ftb.mods.*}/{@code xaero.pac.*} types;
 * those optional dependencies only get touched inside methods already guarded by a
 * {@code ModCompat} check.
 */
public final class ClaimPricingBroadcast {
    private ClaimPricingBroadcast() {
    }

    public static void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, createPayload(player));
    }

    public static PricingBroadcastPayload createPayload(ServerPlayer player) {
        BalanceSnapshot balance = resolveBalanceSnapshot(player);

        return new PricingBroadcastPayload(
                LcClaimEconomyConfig.SERVER.claimPrice.get(),
                LcClaimEconomyConfig.SERVER.forceLoadUpkeepPrice.get(),
                LcClaimEconomyConfig.SERVER.upkeepPeriodMinutes.get(),
                LcClaimEconomyConfig.SERVER.freeChunks.get(),
                balance.claimedChunks(),
                balance.synced(),
                balance.empty(),
                balance.text(),
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

    /** Player's own balance text plus claim count, sourced from whichever claim mod (FTB or OP&C) is actually installed. */
    private record BalanceSnapshot(boolean synced, boolean empty, String text, int claimedChunks) {
        static final BalanceSnapshot NONE = new BalanceSnapshot(false, true, "", 0);
    }

    private static BalanceSnapshot resolveBalanceSnapshot(ServerPlayer player) {
        if (ModCompat.isFtbAvailable() && FTBTeamsAPI.api().isManagerLoaded()) {
            Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
            return team == null ? BalanceSnapshot.NONE : resolveFtbBalance(player, team);
        }
        if (ModCompat.isOpcAvailable()) {
            OpcDashboardSync.Balance balance = OpcDashboardSync.resolve(player);
            return new BalanceSnapshot(balance.synced(), balance.empty(), balance.text(), balance.claimedChunks());
        }
        return BalanceSnapshot.NONE;
    }

    private static BalanceSnapshot resolveFtbBalance(ServerPlayer player, Team team) {
        BankLedgerAccess.ensurePartyAccountExists(player.server, team);
        IBankAccount account = BankLedgerAccess.getAccountForPlayer(player.server, player);
        boolean empty = account.getMoneyStorage().isEmpty();
        String text = empty ? "" : capForNetworkWrite(account.getMoneyStorage().getAllValueText().getString());
        int claimedChunks = FTBChunksAPI.api().isManagerLoaded()
                ? FTBChunksAPI.api().getManager().getOrCreateData(team).getClaimedChunks().size()
                : 0;
        return new BalanceSnapshot(true, empty, text, claimedChunks);
    }

    /**
     * {@code FriendlyByteBuf#writeUtf} throws (and disconnects the player) past its max
     * length. A real formatted balance never gets remotely close to 256 characters, but
     * this keeps a malformed or absurd value from ever being able to take the connection
     * down.
     */
    private static String capForNetworkWrite(String text) {
        return text.length() > 256 ? text.substring(0, 256) : text;
    }
}
