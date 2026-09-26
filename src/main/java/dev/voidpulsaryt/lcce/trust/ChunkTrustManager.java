package dev.voidpulsaryt.lcce.trust;

import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Explicit per-player, per-chunk grants - lets a team (or a private owner) let one specific
 * outsider interact with or build on a single chunk without adding them to the team, buying them
 * in, or opening up a whole region. This is the single highest-precedence check in
 * {@code RegionProtectionListener}: an explicit grant wins over region tiers, marketplace
 * ownership, and even FTB Chunks' own base protection.
 */
public final class ChunkTrustManager extends SavedData {

    private static final String DATA_NAME = "lcce_trust";

    public static final SavedData.Factory<ChunkTrustManager> FACTORY = new SavedData.Factory<>(
            ChunkTrustManager::new,
            (tag, registries) -> load(tag),
            null
    );

    private final Map<ChunkDimPos, Map<UUID, TrustLevel>> trustByChunk = new HashMap<>();

    public static ChunkTrustManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void trust(ChunkDimPos pos, UUID playerId, TrustLevel level) {
        trustByChunk.computeIfAbsent(pos, p -> new HashMap<>()).put(playerId, level);
        setDirty();
    }

    public boolean untrust(ChunkDimPos pos, UUID playerId) {
        Map<UUID, TrustLevel> grants = trustByChunk.get(pos);
        if (grants != null && grants.remove(playerId) != null) {
            if (grants.isEmpty()) {
                trustByChunk.remove(pos);
            }
            setDirty();
            return true;
        }
        return false;
    }

    @Nullable
    public TrustLevel getTrustLevel(ChunkDimPos pos, UUID playerId) {
        Map<UUID, TrustLevel> grants = trustByChunk.get(pos);
        return grants == null ? null : grants.get(playerId);
    }

    public boolean isTrustedFor(ChunkDimPos pos, UUID playerId, TrustLevel required) {
        TrustLevel level = getTrustLevel(pos, playerId);
        return level != null && level.isAtLeast(required);
    }

    public Map<UUID, TrustLevel> getTrustedPlayers(ChunkDimPos pos) {
        return Collections.unmodifiableMap(trustByChunk.getOrDefault(pos, Map.of()));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag chunkList = new ListTag();
        trustByChunk.forEach((pos, grants) -> {
            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putString("Dim", pos.dimension().location().toString());
            chunkTag.putInt("X", pos.x());
            chunkTag.putInt("Z", pos.z());

            ListTag grantList = new ListTag();
            grants.forEach((playerId, level) -> {
                CompoundTag grantTag = new CompoundTag();
                grantTag.putUUID("Player", playerId);
                grantTag.putString("Level", level.name());
                grantList.add(grantTag);
            });
            chunkTag.put("Grants", grantList);
            chunkList.add(chunkTag);
        });
        tag.put("Chunks", chunkList);
        return tag;
    }

    private static ChunkTrustManager load(CompoundTag tag) {
        ChunkTrustManager manager = new ChunkTrustManager();
        ListTag chunkList = tag.getList("Chunks", Tag.TAG_COMPOUND);
        for (Tag ct : chunkList) {
            CompoundTag chunkTag = (CompoundTag) ct;
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(chunkTag.getString("Dim")));
            ChunkDimPos pos = new ChunkDimPos(dim, new ChunkPos(chunkTag.getInt("X"), chunkTag.getInt("Z")));

            Map<UUID, TrustLevel> grants = new HashMap<>();
            ListTag grantList = chunkTag.getList("Grants", Tag.TAG_COMPOUND);
            for (Tag gt : grantList) {
                CompoundTag grantTag = (CompoundTag) gt;
                try {
                    grants.put(grantTag.getUUID("Player"), TrustLevel.valueOf(grantTag.getString("Level")));
                } catch (IllegalArgumentException ignored) {
                    // stale/unknown level name, safe to skip
                }
            }
            manager.trustByChunk.put(pos, grants);
        }
        return manager;
    }
}
