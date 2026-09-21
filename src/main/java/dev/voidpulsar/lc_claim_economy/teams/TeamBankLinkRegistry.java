package dev.voidpulsar.lc_claim_economy.teams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.service.TeamPurgeService;
import io.github.lightman314.lightmanscurrency.api.teams.ITeam;
import io.github.lightman314.lightmanscurrency.api.teams.TeamAPI;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bridges FTB Teams parties to Lightman's Currency team accounts.
 *
 * <p>The persisted link table lives in {@link LcClaimEconomySavedData}; everything here is
 * read/derive logic on top of it plus the periodic sweep ({@link #reconcile}) that drops
 * links whose FTB party no longer exists or has emptied out.
 */
public final class TeamBankLinkRegistry {

    private TeamBankLinkRegistry() {
    }

    // ------------------------------------------------------------------
    // Raw lookups against the saved-data link table
    // ------------------------------------------------------------------

    @Nullable
    public static LcClaimEconomySavedData.TeamLinkEntry findByLcTeamId(MinecraftServer server, long lcTeamId) {
        if (lcTeamId <= 0) {
            return null;
        }
        return LcClaimEconomySavedData.get(server).findByLcTeamId(lcTeamId);
    }

    @Nullable
    public static LcClaimEconomySavedData.TeamLinkEntry findByFtbTeamId(MinecraftServer server, UUID ftbTeamId) {
        return LcClaimEconomySavedData.get(server).get(ftbTeamId);
    }

    @Nullable
    public static ITeam findLcTeam(long lcTeamId) {
        if (lcTeamId <= 0) {
            return null;
        }
        return TeamAPI.getApi().GetTeam(false, lcTeamId);
    }

    /**
     * Resolves the FTB team object for an id, whether it's a solo-player team or a party,
     * as long as it still exists and passes {@link Team#isValid()}.
     */
    @Nullable
    public static Team findStoredTeam(MinecraftServer server, UUID ftbTeamId) {
        if (server == null || ftbTeamId == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return null;
        }
        return FTBTeamsAPI.api().getManager().getTeamByID(ftbTeamId)
                .filter(Team::isValid)
                .orElse(null);
    }

    @Nullable
    public static Team findFtbParty(MinecraftServer server, UUID ftbTeamId) {
        Team resolved = findStoredTeam(server, ftbTeamId);
        return resolved != null && resolved.isPartyTeam() ? resolved : null;
    }

    // ------------------------------------------------------------------
    // "Is this party actually being used right now" checks
    // ------------------------------------------------------------------

    /**
     * A party only counts as in-use if at least one of its members currently has that party
     * selected as their active team (players can belong to a party without it being active).
     */
    public static boolean isFtbPartyInUse(MinecraftServer server, Team party) {
        if (server == null || party == null || !party.isPartyTeam() || !party.isValid()) {
            return false;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return false;
        }

        TeamManager teamManager = FTBTeamsAPI.api().getManager();
        return party.getMembers().stream().anyMatch(memberUuid ->
                teamManager.getTeamForPlayerID(memberUuid)
                        .map(selectedTeam -> selectedTeam.getId().equals(party.getId()))
                        .orElse(false));
    }

    @Nullable
    public static Team findActiveFtbParty(MinecraftServer server, UUID ftbTeamId) {
        Team party = findFtbParty(server, ftbTeamId);
        return party != null && isFtbPartyInUse(server, party) ? party : null;
    }

    public static boolean isOrphanedLink(MinecraftServer server, LcClaimEconomySavedData.TeamLinkEntry entry) {
        return findActiveFtbParty(server, entry.ftbTeamId()) == null;
    }

    public static boolean isLinkedToActiveFtbParty(MinecraftServer server, long lcTeamId) {
        LcClaimEconomySavedData.TeamLinkEntry link = findByLcTeamId(server, lcTeamId);
        return link != null && findActiveFtbParty(server, link.ftbTeamId()) != null;
    }

    // ------------------------------------------------------------------
    // Guard checks used by mixins before allowing destructive LC team ops
    // ------------------------------------------------------------------

    public static boolean shouldBlockLcTeamRemoval(MinecraftServer server, long lcTeamId) {
        refreshLcTeamLink(server, lcTeamId);
        return isLinkedToActiveFtbParty(server, lcTeamId);
    }

    public static boolean shouldBlockLcTeamRoleChanges(MinecraftServer server, long lcTeamId) {
        refreshLcTeamLink(server, lcTeamId);
        return isLinkedToActiveFtbParty(server, lcTeamId);
    }

    /**
     * Drops the link for {@code lcTeamId} if its FTB party is gone or has become inactive.
     * Called defensively right before the block checks above so stale links never veto an
     * action that should otherwise be allowed.
     */
    public static void refreshLcTeamLink(MinecraftServer server, long lcTeamId) {
        LcClaimEconomySavedData.TeamLinkEntry link = findByLcTeamId(server, lcTeamId);
        if (link == null) {
            return;
        }

        Team party = findFtbParty(server, link.ftbTeamId());
        if (party == null || !isFtbPartyInUse(server, party)) {
            LcClaimEconomy.LOGGER.info(
                    "Clearing stale FTB link for LC team {} (party {} is not in use)",
                    lcTeamId,
                    link.ftbTeamId()
            );
            clearLcTeamLink(server, link.ftbTeamId());
        }
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    public static void unlinkLcTeam(MinecraftServer server, long lcTeamId) {
        LcClaimEconomySavedData store = LcClaimEconomySavedData.get(server);
        LcClaimEconomySavedData.TeamLinkEntry link = store.findByLcTeamId(lcTeamId);
        if (link != null && store.clearLcTeamLink(link.ftbTeamId())) {
            LcClaimEconomy.LOGGER.info(
                    "Unlinked LC team {} from FTB team {}",
                    lcTeamId,
                    link.ftbTeamId()
            );
        }
    }

    public static void unlinkFtbParty(MinecraftServer server, UUID ftbTeamId) {
        if (LcClaimEconomySavedData.get(server).clearLcTeamLink(ftbTeamId)) {
            LcClaimEconomy.LOGGER.info("Removed LC link for FTB team {}", ftbTeamId);
        }
    }

    public static void clearLcTeamLink(MinecraftServer server, UUID ftbTeamId) {
        if (LcClaimEconomySavedData.get(server).clearLcTeamLink(ftbTeamId)) {
            LcClaimEconomy.LOGGER.info("Cleared LC link for FTB team {}", ftbTeamId);
        }
    }

    // ------------------------------------------------------------------
    // Periodic sweep
    // ------------------------------------------------------------------

    /**
     * Walks every persisted link and drops the ones that no longer make sense: the FTB party
     * was deleted, the LC team behind it was deleted, or the party emptied out. Also makes
     * sure every currently-active party has a link at all. Returns how many links changed.
     */
    public static int reconcile(MinecraftServer server) {
        if (server == null || CurrencyTeamAccess.cache() == null) {
            return 0;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return 0;
        }

        LcClaimEconomySavedData store = LcClaimEconomySavedData.get(server);
        int mutationCount = 0;

        List<LcClaimEconomySavedData.TeamLinkEntry> snapshot = new ArrayList<>(store.getAllLinks());
        for (LcClaimEconomySavedData.TeamLinkEntry link : snapshot) {
            Team storedTeam = findStoredTeam(server, link.ftbTeamId());

            if (storedTeam == null) {
                // TeamManagerEvent.CREATED can fire before FTB Teams has finished its own load(),
                // in which case an empty team list here just means "not loaded yet", not "deleted".
                if (FTBTeamsAPI.api().getManager().getTeams().isEmpty()) {
                    continue;
                }
                // Route through the shared purge path so wars, pending state, and the LC
                // account all get torn down together, the same as the live deletion event does.
                TeamPurgeService.purge(server, link.ftbTeamId(), null);
                mutationCount++;
                LcClaimEconomy.LOGGER.info(
                        "Reconcile: purged stale hook data for deleted FTB team {} (LC team {})",
                        link.ftbTeamId(),
                        link.lcTeamId()
                );
                continue;
            }

            if (link.lcTeamId() > 0 && findLcTeam(link.lcTeamId()) == null) {
                if (store.clearLcTeamLink(link.ftbTeamId())) {
                    mutationCount++;
                    LcClaimEconomy.LOGGER.info(
                            "Cleared stale LC link {} for FTB team {}",
                            link.lcTeamId(),
                            link.ftbTeamId()
                    );
                }
                continue;
            }

            if (storedTeam.isPartyTeam() && link.lcTeamId() > 0 && storedTeam.getMembers().isEmpty()) {
                if (store.clearLcTeamLink(link.ftbTeamId())) {
                    mutationCount++;
                    LcClaimEconomy.LOGGER.info(
                            "Cleared LC link for empty FTB party {}",
                            link.ftbTeamId()
                    );
                }
            }
        }

        for (Team party : TeamRegistry.activeParties(server)) {
            CurrencyTeamLinkService.ensureLinked(server, party);
        }

        return mutationCount;
    }
}
