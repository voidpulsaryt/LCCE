package dev.voidpulsaryt.lcce.region;

import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persists every team's regions.
 * <p>
 * FTB Chunks already has its own team-wide mob-griefing/explosion/PvP toggles and a
 * public/ally/team-member privacy check ({@code ChunkTeamData}), which keep applying as normal to
 * any claimed chunk that isn't assigned to one of our regions. Regions are an <em>override</em>
 * layer on top of that baseline: a chunk with no region just falls through to FTB Chunks' existing
 * behaviour, so {@link #getRegionAt} returning {@code null} is the signal for "let FTB Chunks
 * handle it" rather than something this mod needs its own fallback settings for.
 */
public final class RegionManager extends SavedData {

    private static final String DATA_NAME = "lcce_regions";

    public static final SavedData.Factory<RegionManager> FACTORY = new SavedData.Factory<>(
            RegionManager::new,
            (tag, registries) -> load(tag),
            null
    );

    private final Map<UUID, Region> regionsById = new HashMap<>();
    private final Map<ChunkDimPos, UUID> regionIdByChunk = new HashMap<>();

    public static RegionManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Region createRegion(UUID teamId, String name) {
        Region region = Region.create(teamId, name);
        regionsById.put(region.id(), region);
        setDirty();
        return region;
    }

    public boolean deleteRegion(UUID regionId) {
        Region removed = regionsById.remove(regionId);
        if (removed == null) {
            return false;
        }
        removed.chunks().forEach(regionIdByChunk::remove);
        setDirty();
        return true;
    }

    @Nullable
    public Region getRegion(UUID regionId) {
        return regionsById.get(regionId);
    }

    public List<Region> getRegionsForTeam(UUID teamId) {
        return regionsById.values().stream()
                .filter(r -> r.teamId().equals(teamId))
                .collect(Collectors.toList());
    }

    public Optional<Region> findRegionByName(UUID teamId, String name) {
        return getRegionsForTeam(teamId).stream()
                .filter(r -> r.name().equalsIgnoreCase(name))
                .findFirst();
    }

    /** {@code null} means this chunk isn't part of any region - FTB Chunks' own protection applies as normal. */
    @Nullable
    public Region getRegionAt(ChunkDimPos pos) {
        UUID regionId = regionIdByChunk.get(pos);
        return regionId == null ? null : regionsById.get(regionId);
    }

    /**
     * Assigns a chunk to a region, removing it from whatever region (if any) it was previously in -
     * a chunk belongs to at most one region at a time.
     */
    public void addChunkToRegion(Region region, ChunkDimPos pos) {
        Region previous = getRegionAt(pos);
        if (previous != null) {
            previous.chunks().remove(pos);
        }
        region.chunks().add(pos);
        regionIdByChunk.put(pos, region.id());
        setDirty();
    }

    public void removeChunkFromRegion(Region region, ChunkDimPos pos) {
        region.chunks().remove(pos);
        regionIdByChunk.remove(pos);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag regionList = new ListTag();
        regionsById.values().forEach(r -> regionList.add(r.save()));
        tag.put("Regions", regionList);
        return tag;
    }

    private static RegionManager load(CompoundTag tag) {
        RegionManager manager = new RegionManager();

        ListTag regionList = tag.getList("Regions", Tag.TAG_COMPOUND);
        for (Tag t : regionList) {
            Region region = Region.load((CompoundTag) t);
            manager.regionsById.put(region.id(), region);
            for (ChunkDimPos pos : region.chunks()) {
                manager.regionIdByChunk.put(pos, region.id());
            }
        }

        return manager;
    }
}
