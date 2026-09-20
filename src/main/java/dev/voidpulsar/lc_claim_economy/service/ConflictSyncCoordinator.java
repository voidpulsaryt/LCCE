package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.network.ConflictStateBroadcastPayload;
import dev.voidpulsar.lc_claim_economy.network.ConflictTeamEntry;
import dev.voidpulsar.lc_claim_economy.teams.TeamRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ConflictSyncCoordinator {
    private ConflictSyncCoordinator() {
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, createPayload(player.server, team, player.getUUID()));
    }

    public static void syncToTeam(MinecraftServer server, UUID teamId) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        Team team = TeamRegistry.resolve(server, teamId);
        if (team == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Team playerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
            if (playerTeam != null && playerTeam.getTeamId().equals(teamId)) {
                PacketDistributor.sendToPlayer(player, createPayload(server, team, player.getUUID()));
            }
        }
    }

    /**
     * War costs are derived from each team's live upkeep (including queued
     * protection changes). Push fresh values to every team whose war UI or
     * next upkeep charge depends on the changed team.
     */
    public static void onUpkeepFactorsChanged(MinecraftServer server, Team changedTeam) {
        if (!ConflictService.isEnabled()) {
            return;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded() || !changedTeam.isValid()) {
            return;
        }

        UUID teamId = changedTeam.getTeamId();
        Set<UUID> synced = new HashSet<>();
        syncToTeam(server, teamId);
        synced.add(teamId);

        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        for (LcClaimEconomySavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            if (entry.warTargets().contains(teamId) && synced.add(entry.ftbTeamId())) {
                syncToTeam(server, entry.ftbTeamId());
            }
        }

        if (!ConflictService.isClaimTeam(server, changedTeam)) {
            return;
        }
        for (Team team : TeamRegistry.trackedTeams(server)) {
            if (team.getTeamId().equals(teamId)) {
                continue;
            }
            if (!ConflictService.isClaimTeam(server, team) || team.getOnlineMembers().isEmpty()) {
                continue;
            }
            if (synced.add(team.getTeamId())) {
                syncToTeam(server, team.getTeamId());
            }
        }
    }

    private static ConflictStateBroadcastPayload createPayload(MinecraftServer server, Team team, UUID viewerId) {
        ConflictService.WarCostBreakdown costs = ConflictService.calculateWarCosts(server, team);
        return new ConflictStateBroadcastPayload(
                costs.baseUpkeepCopper(),
                costs.incomingWarCopper(),
                costs.outgoingWarCopper(),
                ConflictService.warMultiplier(),
                toEntries(ConflictService.buildIncomingViews(server, team)),
                toEntries(ConflictService.buildOutgoingViews(server, team)),
                toEntries(ConflictService.buildAvailableTargets(server, team)),
                viewerId != null && ConflictService.canManageWar(team, viewerId),
                WarDeclarationWindow.isOpenNow(),
                WarDeclarationWindow.isEnabled() ? WarDeclarationWindow.describeWindow() : ""
        );
    }

    private static List<ConflictTeamEntry> toEntries(List<ConflictService.WarTeamView> views) {
        return views.stream()
                .map(view -> new ConflictTeamEntry(
                        view.teamId(),
                        view.displayName(),
                        view.targetBaseUpkeepCopper(),
                        view.warCostCopper(),
                        view.status(),
                        view.opponentPendingDeclareOnViewer(),
                        view.blockEditProtected(),
                        view.explosionProtected(),
                        view.pvpProtected()
                ))
                .toList();
    }
}
