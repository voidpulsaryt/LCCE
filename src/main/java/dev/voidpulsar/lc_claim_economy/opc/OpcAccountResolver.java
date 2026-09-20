package dev.voidpulsar.lc_claim_economy.opc;

import dev.voidpulsar.lc_claim_economy.teams.OpcPartySyncService;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Resolves the LC bank account and online members behind an OP&C claim
 * owner ID (a plain player UUID for solo claims, or the party's own UUID
 * for party-owned claims - OP&C uses the party ID as the claim "owner" in
 * that case, see {@link xaero.pac.common.claims.player.api.IPlayerClaimInfoAPI#isPartyOwned()}).
 * Shared by {@link OpcClaimEconomyListener} and {@link OpcBillingCycleService} so
 * both bill against the same account.
 */
public final class OpcAccountResolver {
    private OpcAccountResolver() {
    }

    @Nullable
    public static IBankAccount resolveAccount(MinecraftServer server, UUID owner, boolean partyOwned) {
        if (!partyOwned) {
            return PlayerBankReference.of(owner).get();
        }
        IServerPartyAPI party = resolveParty(server, owner);
        if (party == null) {
            return null;
        }
        return OpcPartySyncService.getBankAccount(server, party);
    }

    @Nullable
    public static IServerPartyAPI resolveParty(MinecraftServer server, UUID partyId) {
        return OpenPACServerAPI.get(server).getPartyManager().getPartyById(partyId);
    }

    public static void notify(MinecraftServer server, UUID owner, boolean partyOwned, Component message) {
        if (!partyOwned) {
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            if (player != null) {
                player.sendSystemMessage(message);
            }
            return;
        }
        IServerPartyAPI party = resolveParty(server, owner);
        if (party == null) {
            return;
        }
        party.getOnlineMemberStream().forEach(player -> player.sendSystemMessage(message));
    }
}
