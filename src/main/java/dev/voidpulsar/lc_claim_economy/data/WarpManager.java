package dev.voidpulsar.lc_claim_economy.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Player-owned warps (see {@code /lcce warp}). Fully independent of team-claim state. */
final class WarpManager {
    private final Map<UUID, Map<String, WarpEntry>> playerWarps = new HashMap<>();
    private final Runnable markDirty;

    WarpManager(Runnable markDirty) {
        this.markDirty = markDirty;
    }

    void load(CompoundTag tag) {
        ListTag warpList = tag.getList("PlayerWarps", Tag.TAG_COMPOUND);
        for (int i = 0; i < warpList.size(); i++) {
            CompoundTag entryTag = warpList.getCompound(i);
            if (!entryTag.hasUUID("OwnerId") || entryTag.getString("Name").isEmpty()) {
                continue;
            }
            Set<String> aliases = new HashSet<>();
            if (entryTag.contains("Aliases", Tag.TAG_LIST)) {
                ListTag aliasList = entryTag.getList("Aliases", Tag.TAG_STRING);
                for (int j = 0; j < aliasList.size(); j++) {
                    aliases.add(aliasList.getString(j));
                }
            }
            WarpEntry entry = new WarpEntry(
                    entryTag.getString("Name"),
                    entryTag.getUUID("OwnerId"),
                    entryTag.getString("OwnerName"),
                    ResourceLocation.parse(entryTag.getString("Dimension")),
                    entryTag.getDouble("X"),
                    entryTag.getDouble("Y"),
                    entryTag.getDouble("Z"),
                    entryTag.getFloat("Yaw"),
                    entryTag.getFloat("Pitch"),
                    entryTag.getString("ChunkKey"),
                    entryTag.getBoolean("Public"),
                    entryTag.getLong("Created"),
                    Set.copyOf(aliases)
            );
            playerWarps.computeIfAbsent(entry.ownerId(), id -> new HashMap<>())
                    .put(entry.name().toLowerCase(Locale.ROOT), entry);
        }
    }

    void save(CompoundTag tag) {
        ListTag warpList = new ListTag();
        for (Map<String, WarpEntry> warps : playerWarps.values()) {
            for (WarpEntry entry : warps.values()) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString("Name", entry.name());
                entryTag.putUUID("OwnerId", entry.ownerId());
                entryTag.putString("OwnerName", entry.ownerName());
                entryTag.putString("Dimension", entry.dimension().toString());
                entryTag.putDouble("X", entry.x());
                entryTag.putDouble("Y", entry.y());
                entryTag.putDouble("Z", entry.z());
                entryTag.putFloat("Yaw", entry.yaw());
                entryTag.putFloat("Pitch", entry.pitch());
                entryTag.putString("ChunkKey", entry.chunkKey());
                entryTag.putBoolean("Public", entry.isPublic());
                entryTag.putLong("Created", entry.createdAtMillis());
                if (!entry.aliases().isEmpty()) {
                    ListTag aliasList = new ListTag();
                    entry.aliases().forEach(alias -> aliasList.add(StringTag.valueOf(alias)));
                    entryTag.put("Aliases", aliasList);
                }
                warpList.add(entryTag);
            }
        }
        tag.put("PlayerWarps", warpList);
    }

    Map<String, WarpEntry> getWarps(UUID ownerId) {
        return Map.copyOf(playerWarps.getOrDefault(ownerId, Map.of()));
    }

    @Nullable
    WarpEntry getWarp(UUID ownerId, String nameLower) {
        Map<String, WarpEntry> warps = playerWarps.get(ownerId);
        return warps == null ? null : warps.get(nameLower);
    }

    /** Looks up a warp by its primary (lowercased) name first, then by alias, both scoped to this one owner. */
    @Nullable
    WarpEntry resolveWarp(UUID ownerId, String nameOrAliasLower) {
        Map<String, WarpEntry> warps = playerWarps.get(ownerId);
        if (warps == null) {
            return null;
        }
        WarpEntry direct = warps.get(nameOrAliasLower);
        if (direct != null) {
            return direct;
        }
        for (WarpEntry entry : warps.values()) {
            if (entry.aliases().contains(nameOrAliasLower)) {
                return entry;
            }
        }
        return null;
    }

    int countWarps(UUID ownerId) {
        Map<String, WarpEntry> warps = playerWarps.get(ownerId);
        return warps == null ? 0 : warps.size();
    }

    /** Stores (or replaces) a warp, keyed by its owner and lowercased name. */
    void setWarp(WarpEntry entry) {
        playerWarps.computeIfAbsent(entry.ownerId(), id -> new HashMap<>())
                .put(entry.name().toLowerCase(Locale.ROOT), entry);
        markDirty.run();
    }

    boolean removeWarp(UUID ownerId, String nameLower) {
        Map<String, WarpEntry> warps = playerWarps.get(ownerId);
        if (warps == null || warps.remove(nameLower) == null) {
            return false;
        }
        if (warps.isEmpty()) {
            playerWarps.remove(ownerId);
        }
        markDirty.run();
        return true;
    }

    /** Every warp (any owner) currently marked public, for browsing/teleporting to other players' warps. */
    List<WarpEntry> getAllPublicWarps() {
        List<WarpEntry> result = new ArrayList<>();
        for (Map<String, WarpEntry> warps : playerWarps.values()) {
            for (WarpEntry entry : warps.values()) {
                if (entry.isPublic()) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    /** Removes any warp (any owner) sitting on this chunk, e.g. after the chunk is unclaimed. */
    boolean clearWarpsInChunk(String chunkKey) {
        boolean changed = false;
        for (Map<String, WarpEntry> warps : playerWarps.values()) {
            Iterator<WarpEntry> iterator = warps.values().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().chunkKey().equals(chunkKey)) {
                    iterator.remove();
                    changed = true;
                }
            }
        }
        if (changed) {
            markDirty.run();
        }
        return changed;
    }
}
