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

/**
 * Pushes a fresh {@link ConflictStateBroadcastPayload} to whichever clients need one - a single
 * player reconnecting or opening the war screen, an entire team after one of its members' views
 * would go stale, or the wider ripple of everyone whose numbers depend on a team that just
 * changed (see {@link #onUpkeepFactorsChanged}).
 */
public final class ConflictSyncCoordinator {
    private ConflictSyncCoordinator() {
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        Team playerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (playerTeam == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, snapshotFor(player.server, playerTeam, player.getUUID()));
    }

    public static void syncToTeam(MinecraftServer server, UUID teamId) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        Team team = TeamRegistry.resolve(server, teamId);
        if (team == null) {
            return;
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            Team onlinePlayerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(online).orElse(null);
            if (onlinePlayerTeam != null && onlinePlayerTeam.getTeamId().equals(teamId)) {
                PacketDistributor.sendToPlayer(online, snapshotFor(server, team, online.getUUID()));
            }
        }
    }

    /**
     * War costs are derived from each team's live upkeep (including queued protection
     * changes), so a change to one team can silently stale-date the numbers several other
     * teams are looking at: the team itself, every team currently at war with it (their
     * outgoing-war line depends on the changed team's upkeep), and - if the changed team is
     * still a live claim team - every other online claim team, since it might newly qualify or
     * stop qualifying as a war target.
     */
    public static void onUpkeepFactorsChanged(MinecraftServer server, Team changedTeam) {
        if (!ConflictService.isEnabled()) {
            return;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded() || !changedTeam.isValid()) {
            return;
        }

        UUID changedTeamId = changedTeam.getTeamId();
        Set<UUID> alreadyPushed = new HashSet<>();
        syncToTeam(server, changedTeamId);
        alreadyPushed.add(changedTeamId);

        LcClaimEconomySavedData ledger = LcClaimEconomySavedData.get(server);
        for (LcClaimEconomySavedData.TeamLinkEntry link : ledger.getAllLinks()) {
            if (link.warTargets().contains(changedTeamId) && alreadyPushed.add(link.ftbTeamId())) {
                syncToTeam(server, link.ftbTeamId());
            }
        }

        if (!ConflictService.isClaimTeam(server, changedTeam)) {
            return;
        }
        for (Team candidate : TeamRegistry.trackedTeams(server)) {
            UUID candidateId = candidate.getTeamId();
            if (candidateId.equals(changedTeamId)) {
                continue;
            }
            boolean worthPushing = ConflictService.isClaimTeam(server, candidate) && !candidate.getOnlineMembers().isEmpty();
            if (worthPushing && alreadyPushed.add(candidateId)) {
                syncToTeam(server, candidateId);
            }
        }
    }

    private static ConflictStateBroadcastPayload snapshotFor(MinecraftServer server, Team team, UUID viewerId) {
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

    private static List<ConflictTeamEntry> toEntries(List<ConflictService.WarTeamView> rows) {
        return rows.stream()
                .map(row -> new ConflictTeamEntry(
                        row.teamId(),
                        row.displayName(),
                        row.targetBaseUpkeepCopper(),
                        row.warCostCopper(),
                        row.status(),
                        row.opponentPendingDeclareOnViewer(),
                        row.blockEditProtected(),
                        row.explosionProtected(),
                        row.pvpProtected()
                ))
                .toList();
    }
}
