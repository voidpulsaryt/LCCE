package dev.voidpulsar.lc_claim_economy.teams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
import dev.voidpulsar.lc_claim_economy.service.ConflictService;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Answers "what kind of FTB team is this, and does the mod care about it".
 *
 * <p>Solo teams and parties that currently have at least one active member drive war,
 * billing and upkeep; a party nobody has selected as their active team is treated as
 * dormant until it wakes back up.
 */
public final class TeamRegistry {

    /** How a given {@link Team} should be treated by the rest of the mod. */
    public enum TeamKind {
        INVALID,
        SINGLE_PLAYER,
        ACTIVE_PARTY,
        INACTIVE_PARTY
    }

    /** Coarse shape of a team before we bother asking whether a party is actually in use. */
    private enum Shape {
        MISSING,
        SOLO,
        PARTY
    }

    private TeamRegistry() {
    }

    // ------------------------------------------------------------------
    // Classification
    // ------------------------------------------------------------------

    private static Shape shapeOf(@Nullable Team team) {
        if (team == null || !team.isValid()) {
            return Shape.MISSING;
        }
        return team.isPartyTeam() ? Shape.PARTY : Shape.SOLO;
    }

    public static TeamKind kindOf(MinecraftServer server, @Nullable Team team) {
        return switch (shapeOf(team)) {
            case MISSING -> TeamKind.INVALID;
            case SOLO -> TeamKind.SINGLE_PLAYER;
            case PARTY -> TeamBankLinkRegistry.isFtbPartyInUse(server, team) ? TeamKind.ACTIVE_PARTY : TeamKind.INACTIVE_PARTY;
        };
    }

    public static boolean isSinglePlayerTeam(@Nullable Team team) {
        return shapeOf(team) == Shape.SOLO;
    }

    public static boolean isPartyTeam(@Nullable Team team) {
        return shapeOf(team) == Shape.PARTY;
    }

    public static boolean isActiveParty(MinecraftServer server, @Nullable Team team) {
        return kindOf(server, team) == TeamKind.ACTIVE_PARTY;
    }

    public static boolean isInactiveParty(MinecraftServer server, @Nullable Team team) {
        return kindOf(server, team) == TeamKind.INACTIVE_PARTY;
    }

    /** True for any team the mod should surface in war, upkeep and billing logic. */
    public static boolean isTracked(MinecraftServer server, @Nullable Team team) {
        TeamKind kind = kindOf(server, team);
        if (kind == TeamKind.ACTIVE_PARTY) {
            return true;
        }
        if (kind == TeamKind.SINGLE_PLAYER) {
            return isActiveSinglePlayerTeam(server, team);
        }
        return false;
    }

    /**
     * A solo team stops counting the moment its one member picks a party as their active
     * team — the personal team object still exists, it just must stop showing up in
     * war/upkeep while parked.
     */
    public static boolean isActiveSinglePlayerTeam(MinecraftServer server, @Nullable Team team) {
        if (!isSinglePlayerTeam(team) || !FTBTeamsAPI.api().isManagerLoaded()) {
            return false;
        }

        TeamManager teamManager = FTBTeamsAPI.api().getManager();
        UUID soloTeamId = team.getTeamId();
        return team.getMembers().stream()
                .anyMatch(memberUuid -> isMemberCurrentlyOn(teamManager, memberUuid, soloTeamId));
    }

    private static boolean isMemberCurrentlyOn(TeamManager teamManager, UUID memberUuid, UUID teamId) {
        return teamManager.getTeamForPlayerID(memberUuid)
                .map(currentTeam -> currentTeam.getTeamId().equals(teamId))
                .orElse(false);
    }

    // ------------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------------

    public static boolean exists(MinecraftServer server, UUID teamId) {
        return resolve(server, teamId) != null;
    }

    @Nullable
    public static Team resolve(MinecraftServer server, UUID teamId) {
        return TeamBankLinkRegistry.findStoredTeam(server, teamId);
    }

    // ------------------------------------------------------------------
    // Bulk listings
    // ------------------------------------------------------------------

    public static List<Team> allStoredTeams(MinecraftServer server) {
        if (server == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return List.of();
        }
        return selectFrom(FTBTeamsAPI.api().getManager().getTeams(), Team::isValid);
    }

    public static List<Team> trackedTeams(MinecraftServer server) {
        return selectFrom(allStoredTeams(server), candidate -> isTracked(server, candidate));
    }

    public static List<Team> singlePlayerTeams(MinecraftServer server) {
        return selectFrom(allStoredTeams(server), TeamRegistry::isSinglePlayerTeam);
    }

    public static List<Team> activeParties(MinecraftServer server) {
        return selectFrom(allStoredTeams(server), candidate -> isActiveParty(server, candidate));
    }

    /** Shared filter step so each listing method above declares its rule instead of repeating a loop. */
    private static List<Team> selectFrom(Collection<Team> source, Predicate<Team> predicate) {
        return source.stream().filter(predicate).toList();
    }

    // ------------------------------------------------------------------
    // Teardown
    // ------------------------------------------------------------------

    /** Tears down every active and pending war involving this team and pings its partners. */
    public static void dissolveWarLinks(MinecraftServer server, UUID teamId) {
        ConflictService.cleanupTeamWars(server, teamId);
    }

    /** Full cleanup pass run once an FTB team is gone (deleted live, or dropped by reconcile). */
    public static void onTeamDeleted(MinecraftServer server, UUID teamId) {
        if (server == null || teamId == null) {
            return;
        }

        dissolveWarLinks(server, teamId);
        clearPendingStateIfAny(server, teamId);

        LcClaimEconomy.LOGGER.debug("Processed team deletion cleanup for {}", teamId);
    }

    private static void clearPendingStateIfAny(MinecraftServer server, UUID teamId) {
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        if (!savedData.getPendingState(teamId).isEmpty()) {
            savedData.setPendingState(teamId, new TeamQueuedChanges());
        }
    }
}
