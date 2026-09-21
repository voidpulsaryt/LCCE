package dev.voidpulsar.lc_claim_economy.service;

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.Protection;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamRank;
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
    private static final UUID ALL_PLAYERS_ID = new UUID(0L, 0L);
    /** Grants any player whose rank on the claim's owning team resolves to {@link TeamRank#ALLY} - i.e. a member of a different, allied team, not this team's own roster. */
    private static final String ALL_ALLIES_REF = "**";
    private static final UUID ALL_ALLIES_ID = new UUID(0L, 1L);

    private ChunkUserPermissionService() {
    }

    public static void syncToPlayer(ServerPlayer player, String chunkKey) {
        MinecraftServer server = player.server;
        ClaimedChunk chunk = resolveClaimedChunk(chunkKey);
        if (chunk == null || chunk.getTeamData().getTeam() == null) {
            PacketDistributor.sendToPlayer(player, SyncChunkUserPermsPayload.empty(chunkKey));
            return;
        }

        Team ownerTeam = chunk.getTeamData().getTeam();
        String normalizedKey = ChunkCoordKey.encode(chunk.getPos());
        boolean canManage = canManageChunkPermissions(player, ownerTeam);
        List<ChunkUserPermissionEntry> entries = buildPermissionEntries(server, ownerTeam.getTeamId(), normalizedKey);

        PacketDistributor.sendToPlayer(player, new SyncChunkUserPermsPayload(normalizedKey, canManage, entries));
    }

    private static List<ChunkUserPermissionEntry> buildPermissionEntries(MinecraftServer server, UUID teamId, String normalizedKey) {
        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(server);
        Map<UUID, Integer> perPlayerFlags = data.getChunkUserPermissions(teamId, normalizedKey);
        List<ChunkUserPermissionEntry> entries = new ArrayList<>(perPlayerFlags.size() + 2);

        int allFlags = ChunkPermissionFlags.sanitize(data.getChunkAllPlayerPermissionFlags(teamId, normalizedKey));
        entries.add(new ChunkUserPermissionEntry(ALL_PLAYERS_ID, "All Players", allFlags, true, false));

        int allyFlags = ChunkPermissionFlags.sanitize(data.getChunkAllyPermissionFlags(teamId, normalizedKey));
        entries.add(new ChunkUserPermissionEntry(ALL_ALLIES_ID, "All Allies", allyFlags, false, true));

        for (Map.Entry<UUID, Integer> entry : perPlayerFlags.entrySet()) {
            int flags = ChunkPermissionFlags.sanitize(entry.getValue() == null ? 0 : entry.getValue());
            if (flags <= 0) {
                continue;
            }
            entries.add(new ChunkUserPermissionEntry(entry.getKey(), resolvePlayerName(server, entry.getKey()), flags, false, false));
        }

        entries.sort(Comparator.comparing(ChunkUserPermissionEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    @Nullable
    private static ClaimedChunk resolveClaimedChunk(String chunkKey) {
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return null;
        }
        ChunkDimPos pos;
        try {
            pos = ChunkCoordKey.toChunkDimPos(chunkKey);
        } catch (RuntimeException ex) {
            return null;
        }
        return FTBChunksAPI.api().getManager().getChunk(pos);
    }

    public static void handleSetRequest(ServerPlayer actor, String chunkKey, String playerRef, int flags) {
        if (!FTBChunksAPI.api().isManagerLoaded() || !FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }

        ClaimedChunk chunk = resolveClaimedChunk(chunkKey);
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

        if (ALL_PLAYERS_REF.equals(playerRef)) {
            applyAllPlayersUpdate(actor, ownerTeam.getTeamId(), normalizedKey, sanitized);
        } else if (ALL_ALLIES_REF.equals(playerRef)) {
            applyAllAlliesUpdate(actor, ownerTeam.getTeamId(), normalizedKey, sanitized);
        } else {
            applySinglePlayerUpdate(actor, ownerTeam.getTeamId(), normalizedKey, playerRef, sanitized);
        }
    }

    private static void applyAllPlayersUpdate(ServerPlayer actor, UUID teamId, String normalizedKey, int sanitizedFlags) {
        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(actor.server);
        boolean changed = data.setChunkAllPlayerPermissionFlags(teamId, normalizedKey, sanitizedFlags);
        if (changed) {
            String messageKey = sanitizedFlags <= 0
                    ? "message.lc_claim_economy.chunk_user_perm_all_removed"
                    : "message.lc_claim_economy.chunk_user_perm_all_updated";
            actor.displayClientMessage(Component.translatable(messageKey), false);
        }
        syncToPlayer(actor, normalizedKey);
    }

    private static void applyAllAlliesUpdate(ServerPlayer actor, UUID teamId, String normalizedKey, int sanitizedFlags) {
        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(actor.server);
        boolean changed = data.setChunkAllyPermissionFlags(teamId, normalizedKey, sanitizedFlags);
        if (changed) {
            String messageKey = sanitizedFlags <= 0
                    ? "message.lc_claim_economy.chunk_user_perm_allies_removed"
                    : "message.lc_claim_economy.chunk_user_perm_allies_updated";
            actor.displayClientMessage(Component.translatable(messageKey), false);
        }
        syncToPlayer(actor, normalizedKey);
    }

    private static void applySinglePlayerUpdate(ServerPlayer actor, UUID teamId, String normalizedKey, String playerRef, int sanitizedFlags) {
        MinecraftServer server = actor.server;
        Optional<UUID> target = resolvePlayerRef(server, playerRef);
        if (target.isEmpty()) {
            actor.displayClientMessage(Component.translatable("message.lc_claim_economy.chunk_user_perm_unknown_player", playerRef), false);
            return;
        }

        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(server);
        boolean changed = data.setChunkUserPermissionFlags(teamId, normalizedKey, target.get(), sanitizedFlags);
        if (changed) {
            String messageKey = sanitizedFlags <= 0
                    ? "message.lc_claim_economy.chunk_user_perm_removed"
                    : "message.lc_claim_economy.chunk_user_perm_updated";
            actor.displayClientMessage(Component.translatable(messageKey, resolvePlayerName(server, target.get())), false);
        }
        syncToPlayer(actor, normalizedKey);
    }

    /**
     * Per-player chunk permissions only ever grant access on top of the default
     * protection wall - they never apply to a player who's already a team member
     * (members go through the normal rank/protection rules instead), and only cover
     * the specific {@link Protection} bit(s) the caller is asking about. A player whose
     * rank on the owning team is exactly {@link TeamRank#ALLY} (a member of a different,
     * allied team - see {@link TeamRankBridge}) is also eligible for whatever the team
     * has separately granted to "All Allies", stacked on top of any per-player/all-players
     * grant they might also individually have.
     */
    public static boolean isExplicitlyAllowed(ServerPlayer player, @Nullable ClaimedChunk chunk, Protection protection) {
        if (player == null || chunk == null || chunk.getTeamData().getTeam() == null) {
            return false;
        }

        Team team = chunk.getTeamData().getTeam();
        TeamRank rank = team.getRankForPlayer(player.getUUID());
        if (rank.isMemberOrBetter()) {
            return false;
        }

        int requiredBit = ChunkPermissionFlags.fromProtection(protection);
        if (requiredBit <= 0) {
            return false;
        }

        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(player.server);
        String key = ChunkCoordKey.encode(chunk.getPos());
        int grantedFlags = data.getChunkUserPermissionFlags(team.getTeamId(), key, player.getUUID())
                | data.getChunkAllPlayerPermissionFlags(team.getTeamId(), key);
        if (rank == TeamRank.ALLY) {
            grantedFlags |= data.getChunkAllyPermissionFlags(team.getTeamId(), key);
        }
        return (ChunkPermissionFlags.sanitize(grantedFlags) & requiredBit) != 0;
    }

    public static void onChunkUnclaimed(MinecraftServer server, String chunkKey) {
        LcClaimEconomySavedData.get(server).clearChunkUserPermissions(chunkKey);
    }

    /** Regular members always manage their own chunks; party officers are required for party-owned claims. */
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

    /** Accepts a raw UUID string, an online player's current name, or a cached name from a past login. */
    private static Optional<UUID> resolvePlayerRef(MinecraftServer server, String playerRef) {
        if (playerRef == null) {
            return Optional.empty();
        }
        String trimmed = playerRef.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(trimmed));
        } catch (IllegalArgumentException notAUuid) {
            // Fall through - treat it as a player name instead.
        }

        ServerPlayer online = server.getPlayerList().getPlayerByName(trimmed);
        if (online != null) {
            return Optional.of(online.getUUID());
        }

        if (server.getProfileCache() != null) {
            return server.getProfileCache().get(trimmed).map(GameProfile::getId);
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
