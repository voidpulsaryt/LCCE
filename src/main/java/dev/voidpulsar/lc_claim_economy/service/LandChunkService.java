package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.data.ChunkPosKey;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamPendingState;
import dev.voidpulsar.lc_claim_economy.network.PendingStateSync;
import dev.voidpulsar.lc_claim_economy.network.SyncLandChunksPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side handling of the two claimed chunk types. Every claimed chunk is
 * a "build chunk" by default; chunks toggled via alt-click in the claim
 * manager become "land chunks" which use the separate land protection
 * properties and are billed per chunk group.
 * <p>
 * Type changes are queued until the next upkeep period (like force loads) so
 * players cannot switch types right before upkeep to dodge protection costs.
 */
public final class LandChunkService {
    private LandChunkService() {
    }

    public static boolean isLandChunk(MinecraftServer server, UUID teamId, ChunkDimPos pos) {
        return LcClaimEconomySavedData.get(server).isLandChunk(teamId, ChunkPosKey.encode(pos));
    }

    public static boolean isLandChunk(MinecraftServer server, ClaimedChunk chunk) {
        Team team = chunk.getTeamData().getTeam();
        return team != null && isLandChunk(server, team.getTeamId(), chunk.getPos());
    }

    /** Convenience for mixin enforcement code running on the server thread. */
    public static boolean isLandChunk(ClaimedChunk chunk) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null && chunk != null && isLandChunk(server, chunk);
    }

    public static int countLandChunks(MinecraftServer server, Team team, ChunkTeamData chunkData) {
        Set<String> landKeys = LcClaimEconomySavedData.get(server).getLandChunks(team.getTeamId());
        if (landKeys.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ClaimedChunk chunk : chunkData.getClaimedChunks()) {
            if (landKeys.contains(ChunkPosKey.encode(chunk.getPos()))) {
                count++;
            }
        }
        return count;
    }

    public static void handleToggleRequest(ServerPlayer player, String chunkKey) {
        ToggleOutcome outcome = toggleChunk(player, chunkKey, false);
        if (outcome == null) {
            return;
        }
        sendSingleToggleMessage(player, outcome);
    }

    public static void handleToggleBatch(ServerPlayer player, List<String> chunkKeys) {
        if (chunkKeys.isEmpty()) {
            return;
        }
        if (chunkKeys.size() == 1) {
            handleToggleRequest(player, chunkKeys.getFirst());
            return;
        }

        int toLand = 0;
        int toBuild = 0;
        int cancelled = 0;
        int denied = 0;
        boolean changed = false;

        for (String chunkKey : chunkKeys) {
            ToggleOutcome outcome = toggleChunk(player, chunkKey, true);
            if (outcome == null) {
                denied++;
                continue;
            }
            changed = true;
            switch (outcome) {
                case LAND -> toLand++;
                case BUILD -> toBuild++;
                case CANCELLED -> cancelled++;
            }
        }

        if (!changed) {
            if (denied > 0) {
                player.displayClientMessage(Component.translatable("message.lc_claim_economy.chunk_type_denied"), false);
            }
            return;
        }

        int queued = toLand + toBuild;
        if (queued > 0) {
            player.displayClientMessage(
                    Component.translatable("message.lc_claim_economy.chunk_type_queued_bulk", queued, toLand, toBuild),
                    false
            );
        }
        if (cancelled > 0) {
            player.displayClientMessage(
                    Component.translatable("message.lc_claim_economy.chunk_type_pending_cancelled_bulk", cancelled),
                    false
            );
        }
    }

    private static void sendSingleToggleMessage(ServerPlayer player, ToggleOutcome outcome) {
        player.displayClientMessage(Component.translatable(
                switch (outcome) {
                    case LAND -> "message.lc_claim_economy.chunk_type_change_pending_land";
                    case BUILD -> "message.lc_claim_economy.chunk_type_change_pending_build";
                    case CANCELLED -> "message.lc_claim_economy.chunk_type_pending_cancelled";
                }
        ), false);
    }

    private enum ToggleOutcome {
        LAND,
        BUILD,
        CANCELLED
    }

    private static ToggleOutcome toggleChunk(ServerPlayer player, String chunkKey, boolean batch) {
        MinecraftServer server = player.server;
        if (!FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            return null;
        }

        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null) {
            return null;
        }

        ChunkDimPos pos = ChunkPosKey.toChunkDimPos(chunkKey);
        ClaimedChunk chunk = FTBChunksAPI.api().getManager().getChunk(pos);
        if (chunk == null
                || chunk.getTeamData().getTeam() == null
                || !chunk.getTeamData().getTeam().getTeamId().equals(team.getTeamId())
                || !team.getRankForPlayer(player.getUUID()).isMemberOrBetter()) {
            if (!batch) {
                player.displayClientMessage(Component.translatable("message.lc_claim_economy.chunk_type_denied"), false);
            }
            return null;
        }

        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        if (savedData.isProtectionLocked(team.getTeamId())) {
            if (!batch) {
                player.displayClientMessage(Component.translatable("message.lc_claim_economy.protection_locked_change"), false);
            }
            return null;
        }

        String normalizedKey = ChunkPosKey.encode(pos);
        TeamPendingState pendingState = savedData.getPendingState(team.getTeamId());

        if (pendingState.isPendingLandChunk(normalizedKey)) {
            savedData.setPendingState(team.getTeamId(), pendingState.withoutPendingLandChunk(normalizedKey));
            PendingStateSync.syncTeam(server, team);
            LcClaimEconomy.LOGGER.info("Team {}: cancelled pending land toggle for chunk {}", team.getShortName(), normalizedKey);
            return ToggleOutcome.CANCELLED;
        }
        if (pendingState.isPendingBuildChunk(normalizedKey)) {
            savedData.setPendingState(team.getTeamId(), pendingState.withoutPendingBuildChunk(normalizedKey));
            PendingStateSync.syncTeam(server, team);
            LcClaimEconomy.LOGGER.info("Team {}: cancelled pending build toggle for chunk {}", team.getShortName(), normalizedKey);
            return ToggleOutcome.CANCELLED;
        }

        boolean currentlyLand = savedData.isLandChunk(team.getTeamId(), normalizedKey);
        TeamPendingState updated = currentlyLand
                ? pendingState.withPendingBuildChunk(normalizedKey)
                : pendingState.withPendingLandChunk(normalizedKey);

        if (!ProtectionService.canAffordNextPeriod(server, team, updated)) {
            if (!batch) {
                player.displayClientMessage(Component.translatable("message.lc_claim_economy.chunk_type_insufficient"), false);
            }
            return null;
        }

        savedData.setPendingState(team.getTeamId(), updated);
        LcClaimEconomy.LOGGER.info("Team {}: chunk {} queued for {} (next upkeep)",
                team.getShortName(), normalizedKey, currentlyLand ? "build" : "land");
        PendingStateSync.syncTeam(server, team);
        return currentlyLand ? ToggleOutcome.BUILD : ToggleOutcome.LAND;
    }

    public static TeamPendingState applyPendingChunkTypes(MinecraftServer server, Team team, TeamPendingState pendingState) {
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        UUID teamId = team.getTeamId();
        TeamPendingState updated = pendingState;
        boolean changedLand = false;

        for (String key : new HashSet<>(pendingState.pendingLandChunks())) {
            if (savedData.setLandChunk(teamId, key, true)) {
                changedLand = true;
            }
            updated = updated.withoutPendingLandChunk(key);
        }
        for (String key : new HashSet<>(pendingState.pendingBuildChunks())) {
            if (savedData.setLandChunk(teamId, key, false)) {
                changedLand = true;
            }
            updated = updated.withoutPendingBuildChunk(key);
        }

        if (changedLand) {
            broadcastLandChunks(server);
        }
        return updated;
    }

    public static void onChunkUnclaimed(MinecraftServer server, ClaimedChunk chunk) {
        String chunkKey = ChunkPosKey.encode(chunk.getPos());
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        boolean changed = savedData.clearLandChunk(chunkKey);
        ChunkUserPermissionService.onChunkUnclaimed(server, chunkKey);
        WarpService.onChunkUnclaimed(server, chunkKey);

        Team team = chunk.getTeamData().getTeam();
        if (team != null) {
            TeamPendingState pending = savedData.getPendingState(team.getTeamId());
            TeamPendingState cleared = pending.withoutChunkTypePending(chunkKey);
            if (cleared != pending) {
                savedData.setPendingState(team.getTeamId(), cleared);
                PendingStateSync.syncTeam(server, team);
            }
        }

        if (changed) {
            broadcastLandChunks(server);
        }
    }

    public static SyncLandChunksPayload createPayload(MinecraftServer server) {
        return new SyncLandChunksPayload(LcClaimEconomySavedData.get(server).getAllLandChunks());
    }

    public static void syncToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, createPayload(player.server));
    }

    public static void broadcastLandChunks(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(createPayload(server));
    }
}
