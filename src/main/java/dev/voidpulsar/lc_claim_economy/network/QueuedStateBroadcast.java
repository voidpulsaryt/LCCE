package dev.voidpulsar.lc_claim_economy.network;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class QueuedStateBroadcast {
    private QueuedStateBroadcast() {
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            LcClaimEconomy.LOGGER.info("[PendingDebug] syncToPlayer {}: team manager not loaded", player.getScoreboardName());
            return;
        }
        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null) {
            LcClaimEconomy.LOGGER.info("[PendingDebug] syncToPlayer {}: no team, sending EMPTY", player.getScoreboardName());
            PacketDistributor.sendToPlayer(player, QueuedStateBroadcastPayload.EMPTY);
            return;
        }
        QueuedStateBroadcastPayload payload = createPayload(player.server, team);
        LcClaimEconomy.LOGGER.info("[PendingDebug] syncToPlayer {}: team={}, properties={}, forceLoads={}, forceUnloads={}",
                player.getScoreboardName(), team.getShortName(),
                payload.pendingProperties(), payload.pendingForceLoads(), payload.pendingForceUnloads());
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void syncTeam(MinecraftServer server, Team team) {
        QueuedStateBroadcastPayload payload = createPayload(server, team);
        LcClaimEconomy.LOGGER.info("[PendingDebug] syncTeam {}: properties={}, forceLoads={}, forceUnloads={}, recipients={}",
                team.getShortName(), payload.pendingProperties(), payload.pendingForceLoads(),
                payload.pendingForceUnloads(), team.getOnlineMembers().size());
        for (ServerPlayer member : team.getOnlineMembers()) {
            PacketDistributor.sendToPlayer(member, payload);
        }
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
