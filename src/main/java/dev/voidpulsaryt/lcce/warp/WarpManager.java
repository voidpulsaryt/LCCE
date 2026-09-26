package dev.voidpulsaryt.lcce.warp;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persists every player's personal warps, server-wide. */
public final class WarpManager extends SavedData {

    private static final String DATA_NAME = "lcce_warps";

    public static final SavedData.Factory<WarpManager> FACTORY = new SavedData.Factory<>(
            WarpManager::new,
            (tag, registries) -> load(tag),
            null
    );

    private final Map<UUID, Map<String, WarpPoint>> warpsByPlayer = new HashMap<>();

    public static WarpManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void setWarp(UUID playerId, String name, WarpPoint point) {
        warpsByPlayer.computeIfAbsent(playerId, id -> new HashMap<>()).put(name.toLowerCase(), point);
        setDirty();
    }

    public boolean deleteWarp(UUID playerId, String name) {
        Map<String, WarpPoint> warps = warpsByPlayer.get(playerId);
        if (warps != null && warps.remove(name.toLowerCase()) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    @Nullable
    public WarpPoint getWarp(UUID playerId, String name) {
        Map<String, WarpPoint> warps = warpsByPlayer.get(playerId);
        return warps == null ? null : warps.get(name.toLowerCase());
    }

    public Map<String, WarpPoint> getWarpsFor(UUID playerId) {
        return Collections.unmodifiableMap(warpsByPlayer.getOrDefault(playerId, Map.of()));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag playerList = new ListTag();
        warpsByPlayer.forEach((playerId, warps) -> {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("Player", playerId);
            ListTag warpList = new ListTag();
            warps.forEach((name, point) -> {
                CompoundTag warpTag = point.save();
                warpTag.putString("Name", name);
                warpList.add(warpTag);
            });
            playerTag.put("Warps", warpList);
            playerList.add(playerTag);
        });
        tag.put("Players", playerList);
        return tag;
    }

    private static WarpManager load(CompoundTag tag) {
        WarpManager manager = new WarpManager();
        ListTag playerList = tag.getList("Players", Tag.TAG_COMPOUND);
        for (Tag pt : playerList) {
            CompoundTag playerTag = (CompoundTag) pt;
            UUID playerId = playerTag.getUUID("Player");
            Map<String, WarpPoint> warps = new HashMap<>();
            ListTag warpList = playerTag.getList("Warps", Tag.TAG_COMPOUND);
            for (Tag wt : warpList) {
                CompoundTag warpTag = (CompoundTag) wt;
                warps.put(warpTag.getString("Name"), WarpPoint.load(warpTag));
            }
            manager.warpsByPlayer.put(playerId, warps);
        }
        return manager;
    }
}
