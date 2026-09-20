package dev.voidpulsar.lc_claim_economy.service;

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.Protection;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.data.ChunkCoordKey;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.network.ChunkUserPermissionEntry;
import dev.voidpulsar.lc_claim_economy.network.SyncChunkUserPermsPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ChunkUserPermissionService {
    private static final String ALL_PLAYERS_REF = "*";

    private ChunkUserPermissionService() {
    }

    public static void syncToPlayer(ServerPlayer player, String chunkKey) {
        MinecraftServer server = player.server;
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            PacketDistributor.sendToPlayer(player, SyncChunkUserPermsPayload.empty(chunkKey));
            return;
        }

        ChunkDimPos pos;
        try {
            pos = ChunkCoordKey.toChunkDimPos(chunkKey);
        } catch (RuntimeException ex) {
            PacketDistributor.sendToPlayer(player, SyncChunkUserPermsPayload.empty(chunkKey));
            return;
        }

        ClaimedChunk chunk = FTBChunksAPI.api().getManager().getChunk(pos);
        if (chunk == null || chunk.getTeamData().getTeam() == null) {
            PacketDistributor.sendToPlayer(player, SyncChunkUserPermsPayload.empty(chunkKey));
            return;
        }

        Team ownerTeam = chunk.getTeamData().getTeam();
        String normalizedKey = ChunkCoordKey.encode(chunk.getPos());
        boolean canManage = canManageChunkPermissions(player, ownerTeam);

        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(server);
        Map<UUID, Integer> map = data.getChunkUserPermissions(ownerTeam.getTeamId(), normalizedKey);
        List<ChunkUserPermissionEntry> entries = new ArrayList<>(map.size() + 1);

        int allFlags = ChunkPermissionFlags.sanitize(data.getChunkAllPlayerPermissionFlags(ownerTeam.getTeamId(), normalizedKey));
        entries.add(new ChunkUserPermissionEntry(
            new UUID(0L, 0L),
            "All Players",
            allFlags,
            true
        ));

        for (Map.Entry<UUID, Integer> entry : map.entrySet()) {
            int flags = ChunkPermissionFlags.sanitize(entry.getValue() == null ? 0 : entry.getValue());
            if (flags <= 0) {
                continue;
            }
            entries.add(new ChunkUserPermissionEntry(
                    entry.getKey(),
                    resolvePlayerName(server, entry.getKey()),
                flags,
                false
            ));
        }
        entries.sort(Comparator.comparing(ChunkUserPermissionEntry::displayName, String.CASE_INSENSITIVE_ORDER));

        PacketDistributor.sendToPlayer(player, new SyncChunkUserPermsPayload(normalizedKey, canManage, entries));
    }

    public static void handleSetRequest(ServerPlayer actor, String chunkKey, String playerRef, int flags) {
        MinecraftServer server = actor.server;
        if (!FTBChunksAPI.api().isManagerLoaded() || !FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }

        ChunkDimPos pos;
        try {
            pos = ChunkCoordKey.toChunkDimPos(chunkKey);
        } catch (RuntimeException ex) {
            actor.displayClientMessage(Component.translatable("message.lc_claim_economy.chunk_user_perm_invalid_chunk"), false);
            return;
        }

        ClaimedChunk chunk = FTBChunksAPI.api().getManager().getChunk(pos);
        if (chunk == null || chunk.getTeamData().getTeam() == null) {
            actor.displayClientMessage(Component.translatable("message.lc_claim_economy.chunk_user_perm_invalid_chunk"), false);
            return;
        }

        Team ownerTeam = chunk.getTeamData().getTeam();
        if (!canManageChunkPermissions(actor, ownerTeam)) {
            actor.displayClientMessage(Component.translatable("message.lc_claim_economy.chunk_user_perm_denied"), false);
            return;
        }

        String normalizedKey = ChunkCoordKey.encode(chunk.getPos());
        int sanitized = ChunkPermissionFlags.sanitize(flags);

        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(server);

        if (ALL_PLAYERS_REF.equals(playerRef)) {
            boolean changed = data.setChunkAllPlayerPermissionFlags(ownerTeam.getTeamId(), normalizedKey, sanitized);
            if (!changed) {
                syncToPlayer(actor, normalizedKey);
                return;
            }

            if (sanitized <= 0) {
                actor.displayClientMessage(Component.translatable("message.lc_claim_economy.chunk_user_perm_all_removed"), false);
            } else {
                actor.displayClientMessage(Component.translatable("message.lc_claim_economy.chunk_user_perm_all_updated"), false);
            }

            syncToPlayer(actor, normalizedKey);
            return;
        }

        Optional<UUID> target = resolvePlayerRef(server, playerRef);
        if (target.isEmpty()) {
            actor.displayClientMessage(Component.translatable("message.lc_claim_economy.chunk_user_perm_unknown_player", playerRef), false);
            return;
        }

        boolean changed = data.setChunkUserPermissionFlags(ownerTeam.getTeamId(), normalizedKey, target.get(), sanitized);
        if (!changed) {
            syncToPlayer(actor, normalizedKey);
            return;
        }

        if (sanitized <= 0) {
            actor.displayClientMessage(Component.translatable("message.lc_claim_economy.chunk_user_perm_removed", resolvePlayerName(server, target.get())), false);
        } else {
            actor.displayClientMessage(Component.translatable("message.lc_claim_economy.chunk_user_perm_updated", resolvePlayerName(server, target.get())), false);
        }

        syncToPlayer(actor, normalizedKey);
    }

    public static boolean isExplicitlyAllowed(ServerPlayer player, @Nullable ClaimedChunk chunk, Protection protection) {
        if (player == null || chunk == null || chunk.getTeamData().getTeam() == null) {
            return false;
        }

        Team team = chunk.getTeamData().getTeam();
        if (team.getRankForPlayer(player.getUUID()).isMemberOrBetter()) {
            return false;
        }

        int required = ChunkPermissionFlags.fromProtection(protection);
        if (required <= 0) {
            return false;
        }

        MinecraftServer server = player.server;
        String key = ChunkCoordKey.encode(chunk.getPos());
        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(server);
        int flags = data.getChunkUserPermissionFlags(team.getTeamId(), key, player.getUUID())
            | data.getChunkAllPlayerPermissionFlags(team.getTeamId(), key);
        return (ChunkPermissionFlags.sanitize(flags) & required) != 0;
    }

    public static void onChunkUnclaimed(MinecraftServer server, String chunkKey) {
        LcClaimEconomySavedData.get(server).clearChunkUserPermissions(chunkKey);
    }

    private static boolean canManageChunkPermissions(ServerPlayer player, Team ownerTeam) {
        Team playerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (playerTeam == null || !playerTeam.getTeamId().equals(ownerTeam.getTeamId())) {
            return false;
        }
        if (!ownerTeam.isPartyTeam()) {
            return true;
        }
        return ownerTeam.getRankForPlayer(player.getUUID()).isOfficerOrBetter();
    }

    private static Optional<UUID> resolvePlayerRef(MinecraftServer server, String playerRef) {
        if (playerRef == null) {
            return Optional.empty();
        }

        String value = playerRef.trim();
        if (value.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
        }

        ServerPlayer online = server.getPlayerList().getPlayerByName(value);
        if (online != null) {
            return Optional.of(online.getUUID());
        }

        if (server.getProfileCache() != null) {
            Optional<GameProfile> profile = server.getProfileCache().get(value);
            if (profile.isPresent()) {
                return Optional.of(profile.get().getId());
            }
        }

        return Optional.empty();
    }

    private static String resolvePlayerName(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        if (server.getProfileCache() != null) {
            Optional<GameProfile> profile = server.getProfileCache().get(id);
            if (profile.isPresent()) {
                return profile.get().getName();
            }
        }
        return id.toString();
    }
}
