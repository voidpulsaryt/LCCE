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

    public static String encode(ResourceKey<Level> dimension, XZ pos) {
        return encode(dimension.location(), pos.x(), pos.z());
    }

    public static String encode(ChunkDimPos pos) {
        return encode(pos.dimension().location(), pos.x(), pos.z());
    }

    public static String encode(ResourceLocation dimension, int x, int z) {
        return dimension + SEPARATOR + x + SEPARATOR + z;
    }

    public static ResourceLocation dimension(String key) {
        return ResourceLocation.parse(key.substring(0, key.indexOf(SEPARATOR)));
    }

    public static int x(String key) {
        int dimEnd = key.indexOf(SEPARATOR);
        int xEnd = key.indexOf(SEPARATOR, dimEnd + 1);
        return Integer.parseInt(key.substring(dimEnd + 1, xEnd));
    }

    public static int z(String key) {
        int xEnd = key.indexOf(SEPARATOR, key.indexOf(SEPARATOR) + 1);
        return Integer.parseInt(key.substring(xEnd + 1));
    }

    public static ChunkDimPos toChunkDimPos(String key) {
        ResourceLocation dimensionId = dimension(key);
        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
        return new ChunkDimPos(dimensionKey, x(key), z(key));
    }
}
