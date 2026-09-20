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
 * Central view of which FTB teams exist and how the mod should treat them.
 * Single-player teams and active parties participate in war, upkeep, and billing;
 * inactive/empty parties are ignored until they become active again.
 */
public final class TeamRegistry {
    public enum TeamKind {
        INVALID,
        SINGLE_PLAYER,
        ACTIVE_PARTY,
        INACTIVE_PARTY
    }

    private TeamRegistry() {
    }

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

    /** Teams that participate in war, upkeep, and other mod features. */
    public static boolean isTracked(MinecraftServer server, @Nullable Team team) {
        TeamKind kind = kindOf(server, team);
        return switch (kind) {
            case ACTIVE_PARTY -> true;
            case SINGLE_PLAYER -> isActiveSinglePlayerTeam(server, team);
            default -> false;
        };
    }

    /**
     * A solo FTB team counts only while at least one member still uses it as their active team.
     * Players who joined a party keep their personal team object, but it must not appear in war/upkeep.
     */
    public static boolean isActiveSinglePlayerTeam(MinecraftServer server, @Nullable Team team) {
        if (!isSinglePlayerTeam(team) || !FTBTeamsAPI.api().isManagerLoaded()) {
            return false;
        }

        TeamManager manager = FTBTeamsAPI.api().getManager();
        for (UUID memberId : team.getMembers()) {
            if (manager.getTeamForPlayerID(memberId)
                    .map(activeTeam -> activeTeam.getTeamId().equals(team.getTeamId()))
                    .orElse(false)) {
                return true;
            }
        }
        return false;
    }

    public static boolean exists(MinecraftServer server, UUID teamId) {
        return resolve(server, teamId) != null;
    }

    @Nullable
    public static Team resolve(MinecraftServer server, UUID teamId) {
        return TeamBankLinkRegistry.findStoredTeam(server, teamId);
    }

    public static List<Team> allStoredTeams(MinecraftServer server) {
        if (server == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return List.of();
        }
        List<Team> teams = new ArrayList<>();
        for (Team team : FTBTeamsAPI.api().getManager().getTeams()) {
            if (team.isValid()) {
                teams.add(team);
            }
        }
        return teams;
    }

    public static List<Team> trackedTeams(MinecraftServer server) {
        List<Team> tracked = new ArrayList<>();
        for (Team team : allStoredTeams(server)) {
            if (isTracked(server, team)) {
                tracked.add(team);
            }
        }
        return tracked;
    }

    public static List<Team> singlePlayerTeams(MinecraftServer server) {
        List<Team> singles = new ArrayList<>();
        for (Team team : allStoredTeams(server)) {
            if (isSinglePlayerTeam(team)) {
                singles.add(team);
            }
        }
        return singles;
    }

    public static List<Team> activeParties(MinecraftServer server) {
        List<Team> parties = new ArrayList<>();
        for (Team team : allStoredTeams(server)) {
            if (isActiveParty(server, team)) {
                parties.add(team);
            }
        }
        return parties;
    }

    /**
     * Removes all active and pending war links for the given team and refreshes partners.
     */
    public static void dissolveWarLinks(MinecraftServer server, UUID teamId) {
        ConflictService.cleanupTeamWars(server, teamId);
    }

    /**
     * Cleans up mod state when an FTB team is deleted or its saved link is reconciled away.
     */
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
