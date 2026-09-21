package dev.voidpulsar.lc_claim_economy.network;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Builds and sends {@link QueuedStateBroadcastPayload}s. Split out from {@code
 * ChunkUserPermissionService}/other services that trigger a sync so callers pushing to a whole
 * team don't need to re-resolve {@link TeamQueuedChanges} per online member themselves.
 */
public final class QueuedStateBroadcast {
    private QueuedStateBroadcast() {
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null) {
            PacketDistributor.sendToPlayer(player, QueuedStateBroadcastPayload.EMPTY);
            return;
        }
        QueuedStateBroadcastPayload payload = createPayload(player.server, team);
        logPlayerSync(player, team, payload);
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void syncTeam(MinecraftServer server, Team team) {
        QueuedStateBroadcastPayload payload = createPayload(server, team);
        logTeamSync(team, payload);
        dispatchToOnlineMembers(team, payload);
    }

    private static void dispatchToOnlineMembers(Team team, QueuedStateBroadcastPayload payload) {
        for (ServerPlayer member : team.getOnlineMembers()) {
            PacketDistributor.sendToPlayer(member, payload);
        }
    }

    private static void logPlayerSync(ServerPlayer player, Team team, QueuedStateBroadcastPayload payload) {
        LcClaimEconomy.LOGGER.debug("syncToPlayer {}: team={}, properties={}, forceLoads={}, forceUnloads={}",
                player.getScoreboardName(), team.getShortName(),
                payload.pendingProperties(), payload.pendingForceLoads(), payload.pendingForceUnloads());
    }

    private static void logTeamSync(Team team, QueuedStateBroadcastPayload payload) {
        LcClaimEconomy.LOGGER.debug("syncTeam {}: properties={}, forceLoads={}, forceUnloads={}, recipients={}",
                team.getShortName(), payload.pendingProperties(), payload.pendingForceLoads(),
                payload.pendingForceUnloads(), team.getOnlineMembers().size());
    }

    public static QueuedStateBroadcastPayload createPayload(MinecraftServer server, Team team) {
        TeamQueuedChanges pendingState = LcClaimEconomySavedData.get(server).getPendingState(team.getTeamId());
        return new QueuedStateBroadcastPayload(
                pendingState.pendingProperties(),
                pendingState.pendingForceLoads(),
                pendingState.pendingForceUnloads(),
                pendingState.pendingLandChunks(),
                pendingState.pendingBuildChunks()
        );
    }
}
