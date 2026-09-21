package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
import dev.voidpulsar.lc_claim_economy.teams.CurrencyTeamLinkService;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The single funnel for tearing down everything this mod attaches to an FTB team once
 * that team stops existing. It matters that every deletion path - live event
 * listeners, the startup reconcile pass, and disband settlement - all route through
 * here, otherwise it's easy for one of them to forget a step and leave orphaned state
 * behind in {@code SavedData}.
 *
 * <p>Note this class does NOT do the financial side (chunk refunds, handing the
 * remaining balance to the owner). {@link PartyDissolutionSettlement} does that first,
 * before this runs, because it still needs the live {@link Team} and its member list -
 * something this class deliberately doesn't require, since the reconcile path may only
 * have a bare team id to work with.
 */
public final class TeamPurgeService {
    private TeamPurgeService() {
    }

    /**
     * Tears down war links, pending state, the linked bank account, and the SavedData
     * entry itself. {@code teamForLcCleanup} may be {@code null} (the reconcile path
     * doesn't have a live {@link Team} to hand over), in which case the bank-account
     * link is dropped directly instead of routed through the normal deletion hook.
     */
    public static void purge(MinecraftServer server, UUID teamId, @Nullable Team teamForLcCleanup) {
        if (server == null || teamId == null) {
            return;
        }

        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);

        ConflictService.cleanupTeamWars(server, teamId);

        if (!savedData.getPendingState(teamId).isEmpty()) {
            savedData.setPendingState(teamId, new TeamQueuedChanges());
        }

        if (teamForLcCleanup != null) {
            // CurrencyTeamLinkService has the package access this class doesn't, to reach
            // into CurrencyTeamAccess for the actual LC-side bank team deletion.
            CurrencyTeamLinkService.onTeamDeleted(server, teamForLcCleanup);
        } else {
            // No live Team object to hand off (reconcile path) - at minimum, drop our own
            // SavedData link so it doesn't keep pointing at an account nothing references
            // anymore. The LC-side team is left alone; a later reconcile pass on that side
            // will catch it.
            if (savedData.removeLink(teamId) != null) {
                LcClaimEconomy.LOGGER.info("Reconcile: removed stale SavedData entry for team {}", teamId);
            }
        }
    }

    /** Used by event handlers that still have the live {@link Team} in hand. */
    public static void purge(MinecraftServer server, Team team) {
        purge(server, team.getTeamId(), team);
    }
}
