package dev.voidpulsar.lc_claim_economy.opc;

import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.claims.player.api.IPlayerClaimInfoAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import java.util.UUID;

/**
 * Resolves the balance/claim-count data {@code ClaimPricingBroadcast} needs for
 * the dashboard, for a given player's OP&C claim identity. Kept in this
 * package (rather than inline in {@code ClaimPricingBroadcast}) so that class's
 * own method signatures never reference any {@code xaero.pac.*} type -
 * see {@code ModCompat}'s javadoc on why that matters for FTB-only
 * installs that don't have OP&C at all.
 */
public final class OpcDashboardSync {
    private OpcDashboardSync() {
    }

    public record Balance(boolean synced, boolean empty, String text, int claimedChunks) {
        public static final Balance NONE = new Balance(false, true, "", 0);
    }

    /**
     * A player's OP&C claim identity for dashboard purposes: their party
     * (if they're in one - OP&C parties always own claims under the
     * party's own UUID, see {@link OpcAccountResolver}) or themselves.
     * This intentionally doesn't inspect actual claim ownership the way
     * {@link OpcClaimEconomyListener} does per-chunk, since the dashboard
     * shows one account regardless of whether the player has claimed
     * anything yet.
     */
    public static Balance resolve(ServerPlayer player) {
        IServerPartyAPI party = OpenPACServerAPI.get(player.server).getPartyManager().getPartyByMember(player.getUUID());
        UUID owner = party != null ? party.getId() : player.getUUID();
        boolean partyOwned = party != null;

        IServerClaimsManagerAPI claimsManager = OpenPACServerAPI.get(player.server).getServerClaimsManager();
        IPlayerClaimInfoAPI info = claimsManager.getPlayerInfo(owner);
        int claimedChunks = info != null ? info.getClaimCount() : 0;

        IBankAccount account = OpcAccountResolver.resolveAccount(player.server, owner, partyOwned);
        if (account == null) {
            return new Balance(false, true, "", claimedChunks);
        }

        boolean empty = account.getMoneyStorage().isEmpty();
        String text = "";
        if (!empty) {
            text = account.getMoneyStorage().getAllValueText().getString();
            if (text.length() > 256) {
                text = text.substring(0, 256);
            }
        }
        return new Balance(true, empty, text, claimedChunks);
    }
}
