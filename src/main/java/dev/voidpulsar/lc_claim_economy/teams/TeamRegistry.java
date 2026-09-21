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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    private TeamRegistry() {
    }

    // ------------------------------------------------------------------
    // Classification
    // ------------------------------------------------------------------

    public static TeamKind kindOf(MinecraftServer server, @Nullable Team team) {
        if (team == null || !team.isValid()) {
            return TeamKind.INVALID;
        }
        if (!team.isPartyTeam()) {
            return TeamKind.SINGLE_PLAYER;
        }
        return TeamBankLinkRegistry.isFtbPartyInUse(server, team)
                ? TeamKind.ACTIVE_PARTY
                : TeamKind.INACTIVE_PARTY;
    }

    public static boolean isSinglePlayerTeam(@Nullable Team team) {
        return team != null && team.isValid() && !team.isPartyTeam();
    }

    public static boolean isPartyTeam(@Nullable Team team) {
        return team != null && team.isValid() && team.isPartyTeam();
    }

    public static boolean isActiveParty(MinecraftServer server, @Nullable Team team) {
        return kindOf(server, team) == TeamKind.ACTIVE_PARTY;
    }

    public static boolean isInactiveParty(MinecraftServer server, @Nullable Team team) {
        return kindOf(server, team) == TeamKind.INACTIVE_PARTY;
    }

    /** True for any team the mod should surface in war, upkeep and billing logic. */
    public static boolean isTracked(MinecraftServer server, @Nullable Team team) {
        return switch (kindOf(server, team)) {
            case ACTIVE_PARTY -> true;
            case SINGLE_PLAYER -> isActiveSinglePlayerTeam(server, team);
            case INACTIVE_PARTY, INVALID -> false;
        };
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
        return team.getMembers().stream().anyMatch(memberUuid ->
                teamManager.getTeamForPlayerID(memberUuid)
                        .map(currentTeam -> currentTeam.getTeamId().equals(soloTeamId))
                        .orElse(false));
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
        List<Team> validTeams = new ArrayList<>();
        for (Team candidate : FTBTeamsAPI.api().getManager().getTeams()) {
            if (candidate.isValid()) {
                validTeams.add(candidate);
            }
        }
        return validTeams;
    }

    public static List<Team> trackedTeams(MinecraftServer server) {
        return allStoredTeams(server).stream()
                .filter(candidate -> isTracked(server, candidate))
                .toList();
    }

    public static List<Team> singlePlayerTeams(MinecraftServer server) {
        return allStoredTeams(server).stream()
                .filter(TeamRegistry::isSinglePlayerTeam)
                .toList();
    }

    public static List<Team> activeParties(MinecraftServer server) {
        return allStoredTeams(server).stream()
                .filter(candidate -> isActiveParty(server, candidate))
                .toList();
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

        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        if (!savedData.getPendingState(teamId).isEmpty()) {
            savedData.setPendingState(teamId, new TeamQueuedChanges());
        }

        LcClaimEconomy.LOGGER.debug("Processed team deletion cleanup for {}", teamId);
    }
}
