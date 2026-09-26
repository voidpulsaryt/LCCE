package dev.voidpulsaryt.lcce.client.xaero;

import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.TeamProperties;
import dev.voidpulsaryt.lcce.client.ClientRegionCache;
import dev.voidpulsaryt.lcce.network.RegionSyncPayload.RegionEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import xaero.map.highlight.ChunkHighlighter;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Colors every claimed chunk on Xaero's World Map by its team's own color (the same
 * {@code TeamProperties.COLOR} FTB Teams itself shows in its UI), with a solid border between a
 * claimed chunk and any unclaimed, differently-owned, or differently-*regioned* neighbor - the
 * nested sub-region outline from the reference mockups, one shade lighter than the team-vs-team
 * border so a region reads as "inside this team's claim" rather than a separate claim entirely.
 * Registered into {@code xaero.map.highlight.HighlighterRegistry} via {@link WorldMapSessionMixin},
 * the only place that registry is reachable from - modeled directly on the (real, shipped)
 * {@code ftbxaerocompat} mod's own {@code ClaimsHighlighter}, down to the same FTB Chunks
 * client-side {@code MapChunk}/{@code MapDimension} API, since that's proven correct and needs
 * zero manual screen-position math (Xaero's own chunk-tile renderer draws these, at whatever
 * position it draws the chunk itself). Region membership comes from {@link ClientRegionCache},
 * populated by {@code RegionSyncPayload} - this mod's own data, so unlike team ownership FTB
 * Chunks has no client-side copy of it already.
 */
public final class LcceClaimHighlighter extends ChunkHighlighter {

    public LcceClaimHighlighter() {
        super(true);
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> key, int regionX, int regionZ) {
        return MapDimension.getCurrent().map(dim -> dim.getRegion(XZ.of(regionX, regionZ)) != null).orElse(false);
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> key, int x, int z) {
        return MapDimension.getCurrent()
                .map(dim -> getChunk(dim, x, z))
                .map(chunk -> chunk.getTeam().isPresent())
                .orElse(false);
    }

    @Override
    protected int[] getColors(ResourceKey<Level> key, int x, int z) {
        Optional<MapDimension> dimOpt = MapDimension.getCurrent();
        if (dimOpt.isEmpty()) {
            return null;
        }
        MapDimension dim = dimOpt.get();
        MapChunk center = getChunk(dim, x, z);
        Optional<Team> teamOpt = center.getTeam();
        if (teamOpt.isEmpty()) {
            return null;
        }

        // Xaero's highlight blending expects an unusual packed format - blue in the highest byte,
        // green next, red next, alpha (opacity) in the lowest byte - confirmed against the real
        // ftbxaerocompat mod's own (proven-working) ClaimsHighlighter rather than guessed.
        Color4I teamColor = teamOpt.get().getProperty(TeamProperties.COLOR);
        int rgb = teamColor.rgb() & 0xFFFFFF;
        int packed = ((rgb & 0xFF) << 24) | (((rgb >> 8) & 0xFF) << 16) | (((rgb >> 16) & 0xFF) << 8);
        int fill = (packed & 0xFFFFFF00) | 64;
        int teamEdge = (packed & 0xFFFFFF00) | 176;
        int regionEdge = (packed & 0xFFFFFF00) | 112;

        RegionEntry centerRegion = ClientRegionCache.regionAt(new ChunkDimPos(key, new ChunkPos(x, z)));

        this.resultStore[0] = fill;
        this.resultStore[1] = edgeFor(dim, x, z - 1, center, centerRegion, fill, teamEdge, regionEdge);
        this.resultStore[2] = edgeFor(dim, x + 1, z, center, centerRegion, fill, teamEdge, regionEdge);
        this.resultStore[3] = edgeFor(dim, x, z + 1, center, centerRegion, fill, teamEdge, regionEdge);
        this.resultStore[4] = edgeFor(dim, x - 1, z, center, centerRegion, fill, teamEdge, regionEdge);
        return this.resultStore;
    }

    private static int edgeFor(MapDimension dim, int nx, int nz, MapChunk center, @Nullable RegionEntry centerRegion,
                                int fill, int teamEdge, int regionEdge) {
        MapChunk neighbor = getChunk(dim, nx, nz);
        if (!sameTeam(neighbor, center)) {
            return teamEdge;
        }
        RegionEntry neighborRegion = ClientRegionCache.regionAt(new ChunkDimPos(dim.dimension, new ChunkPos(nx, nz)));
        UUID centerId = centerRegion == null ? null : centerRegion.regionId();
        UUID neighborId = neighborRegion == null ? null : neighborRegion.regionId();
        return java.util.Objects.equals(centerId, neighborId) ? fill : regionEdge;
    }

    private static boolean sameTeam(@Nullable MapChunk a, MapChunk b) {
        if (a == null) {
            return false;
        }
        Optional<Team> aTeam = a.getTeam();
        Optional<Team> bTeam = b.getTeam();
        return aTeam.isPresent() && bTeam.isPresent() && aTeam.get().getId().equals(bTeam.get().getId());
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> key, int regionX, int regionZ) {
        Optional<MapDimension> dimOpt = MapDimension.getCurrent();
        if (dimOpt.isEmpty()) {
            return 0;
        }
        MapDimension dim = dimOpt.get();
        int baseChunkX = regionX * 32;
        int baseChunkZ = regionZ * 32;
        long hash = 0L;
        for (int i = 0; i < 32; i++) {
            for (int j = 0; j < 32; j++) {
                int chunkX = baseChunkX + i;
                int chunkZ = baseChunkZ + j;
                RegionEntry region = ClientRegionCache.regionAt(new ChunkDimPos(key, new ChunkPos(chunkX, chunkZ)));
                hash = accumulate(hash, getChunk(dim, chunkX, chunkZ), region);
            }
        }
        return (int) (hash >> 32) * 37 + (int) (hash & 0xFFFFFFFFL);
    }

    private static long accumulate(long hash, @Nullable MapChunk chunk, @Nullable RegionEntry region) {
        hash *= 37L;
        if (chunk == null) {
            return hash;
        }
        Optional<Team> teamOpt = chunk.getTeam();
        if (teamOpt.isEmpty()) {
            return hash;
        }
        var teamId = teamOpt.get().getId();
        hash += teamId.getLeastSignificantBits();
        hash *= 37L;
        hash += teamId.getMostSignificantBits();
        // Folding the region id (if any) in too, so a chunk being added to/removed from a region -
        // which changes nothing about the chunk's claim itself - still invalidates this region's
        // cached highlight bitmap instead of leaving a stale one until something else does.
        if (region != null) {
            hash *= 37L;
            hash += region.regionId().getLeastSignificantBits();
            hash *= 37L;
            hash += region.regionId().getMostSignificantBits();
        }
        return hash;
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> key, int x, int z) {
        Optional<Component> teamName = MapDimension.getCurrent()
                .map(dim -> getChunk(dim, x, z))
                .flatMap(MapChunk::getTeam)
                .map(Team::getColoredName);
        if (teamName.isEmpty()) {
            return Component.empty();
        }
        RegionEntry region = ClientRegionCache.regionAt(new ChunkDimPos(key, new ChunkPos(x, z)));
        return region == null ? teamName.get() : teamName.get().copy().append(" - " + region.name());
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> key, int x, int z) {
        return null;
    }

    @Override
    public void addMinimapBlockHighlightTooltips(java.util.List<Component> list, ResourceKey<Level> key, int x, int z, int width) {
        // No extra minimap tooltip lines needed beyond the subtle/blunt ones above.
    }

    private static MapChunk getChunk(MapDimension dim, int chunkX, int chunkZ) {
        MapRegion region = dim.getRegion(XZ.regionFromChunk(chunkX, chunkZ));
        return region.getChunkForAbsoluteChunkPos(XZ.of(chunkX, chunkZ));
    }
}
