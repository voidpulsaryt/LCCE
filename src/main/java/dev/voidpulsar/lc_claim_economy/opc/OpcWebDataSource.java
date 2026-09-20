package dev.voidpulsar.lc_claim_economy.opc;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import dev.voidpulsar.lc_claim_economy.web.LeaderboardEntry;
import io.github.lightman314.lightmanscurrency.api.misc.player.PlayerReference;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import net.minecraft.server.MinecraftServer;
import xaero.pac.common.claims.player.api.IPlayerClaimInfoAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Only ever called from {@code WebDataService} behind
 * {@code ModCompat.isOpcAvailable()}, and kept in this package (rather than
 * the {@code web} package) so that {@code WebDataService}'s own method
 * signatures never reference any {@code xaero.pac.*} type - see
 * {@code ModCompat}'s javadoc on why that matters for OP&C-less installs.
 */
public final class OpcWebDataSource {
    private OpcWebDataSource() {
    }

    public static List<LeaderboardEntry> collectEntries(MinecraftServer server) {
        IServerClaimsManagerAPI claimsManager = OpenPACServerAPI.get(server).getServerClaimsManager();
        List<LeaderboardEntry> entries = new ArrayList<>();
        claimsManager.getPlayerInfoStream().forEach(info -> {
            try {
                entries.add(toEntry(server, info));
            } catch (Exception e) {
                LcClaimEconomy.LOGGER.warn("Web leaderboard: failed to read OP&C owner {}", info.getPlayerId(), e);
            }
        });
        return entries;
    }

    private static LeaderboardEntry toEntry(MinecraftServer server, IPlayerClaimInfoAPI info) {
        UUID owner = info.getPlayerId();
        boolean partyOwned = info.isPartyOwned();

        String name = partyOwned ? partyName(server, owner) : playerName(server, owner);

        IBankAccount account = OpcAccountResolver.resolveAccount(server, owner, partyOwned);
        long balance = account != null ? CurrencyAmounts.totalCopper(account) : 0L;

        return new LeaderboardEntry(name, balance, info.getClaimCount());
    }

    public static int trackedAccountCount(MinecraftServer server) {
        IServerClaimsManagerAPI claimsManager = OpenPACServerAPI.get(server).getServerClaimsManager();
        return (int) claimsManager.getPlayerInfoStream().count();
    }

    private static String partyName(MinecraftServer server, UUID partyId) {
        IServerPartyAPI party = OpcAccountResolver.resolveParty(server, partyId);
        if (party == null) {
            return partyId.toString();
        }
        return party.getDefaultName();
    }

    private static String playerName(MinecraftServer server, UUID playerId) {
        String name = PlayerReference.getPlayerName(playerId);
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (server.getProfileCache() != null) {
            return server.getProfileCache().get(playerId)
                    .map(com.mojang.authlib.GameProfile::getName)
                    .filter(profileName -> profileName != null && !profileName.isBlank())
                    .orElse(playerId.toString());
        }
        return playerId.toString();
    }
}
