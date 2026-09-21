package dev.voidpulsar.lc_claim_economy.data;

import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftblibrary.math.XZ;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Packs a dimension + chunk-x + chunk-z triple into a single string so it can be a map key
 * in the NBT this mod persists (land-chunk sets, per-chunk permission maps, warp locations,
 * etc.). The {@code dimension#x#z} shape and separator character are on-disk format, not
 * style - a running server already has this exact format baked into its save file, so
 * changing either here would silently stop those saved keys from round-tripping.
 */
public final class ChunkCoordKey {
    private static final String SEPARATOR = "#";

    private ChunkCoordKey() {
    }

    public static String encode(ResourceKey<Level> dimensionKey, XZ chunkPos) {
        return encode(dimensionKey.location(), chunkPos.x(), chunkPos.z());
    }

    public static String encode(ChunkDimPos chunkDimPos) {
        return encode(chunkDimPos.dimension().location(), chunkDimPos.x(), chunkDimPos.z());
    }

    public static String encode(ResourceLocation dimensionId, int chunkX, int chunkZ) {
        return dimensionId + SEPARATOR + chunkX + SEPARATOR + chunkZ;
    }

    public static ResourceLocation dimension(String encodedKey) {
        return ResourceLocation.parse(encodedKey.substring(0, encodedKey.indexOf(SEPARATOR)));
    }

    public static int x(String encodedKey) {
        int dimEnd = encodedKey.indexOf(SEPARATOR);
        int xEnd = encodedKey.indexOf(SEPARATOR, dimEnd + 1);
        return Integer.parseInt(encodedKey.substring(dimEnd + 1, xEnd));
    }

    public static int z(String encodedKey) {
        int xEnd = encodedKey.indexOf(SEPARATOR, encodedKey.indexOf(SEPARATOR) + 1);
        return Integer.parseInt(encodedKey.substring(xEnd + 1));
    }

    public static ChunkDimPos toChunkDimPos(String encodedKey) {
        ResourceLocation resolvedDimensionId = dimension(encodedKey);
        ResourceKey<Level> resolvedDimensionKey = ResourceKey.create(Registries.DIMENSION, resolvedDimensionId);
        return new ChunkDimPos(resolvedDimensionKey, x(encodedKey), z(encodedKey));
    }
}
