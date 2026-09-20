package dev.voidpulsar.lc_claim_economy.data;

import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftblibrary.math.XZ;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

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
        int first = key.indexOf(SEPARATOR);
        int second = key.indexOf(SEPARATOR, first + 1);
        return Integer.parseInt(key.substring(first + 1, second));
    }

    public static int z(String key) {
        int second = key.indexOf(SEPARATOR, key.indexOf(SEPARATOR) + 1);
        return Integer.parseInt(key.substring(second + 1));
    }

    public static ChunkDimPos toChunkDimPos(String key) {
        ResourceLocation dimensionId = dimension(key);
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        return new ChunkDimPos(dimension, x(key), z(key));
    }
}
