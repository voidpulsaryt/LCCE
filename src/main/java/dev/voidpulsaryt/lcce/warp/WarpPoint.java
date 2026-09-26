package dev.voidpulsaryt.lcce.warp;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** A named teleport point a player has set - personal by default, or shared publicly (e.g. a shop). */
public final class WarpPoint {

    private final ResourceKey<Level> dimension;
    private final BlockPos pos;
    private final float yaw;
    private final float pitch;
    private final boolean isPublic;

    public WarpPoint(ResourceKey<Level> dimension, BlockPos pos, float yaw, float pitch, boolean isPublic) {
        this.dimension = dimension;
        this.pos = pos;
        this.yaw = yaw;
        this.pitch = pitch;
        this.isPublic = isPublic;
    }

    public ResourceKey<Level> dimension() { return dimension; }
    public BlockPos pos() { return pos; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public boolean isPublic() { return isPublic; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", dimension.location().toString());
        tag.putInt("X", pos.getX());
        tag.putInt("Y", pos.getY());
        tag.putInt("Z", pos.getZ());
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        tag.putBoolean("Public", isPublic);
        return tag;
    }

    public static WarpPoint load(CompoundTag tag) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dim")));
        BlockPos pos = new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
        return new WarpPoint(dim, pos, tag.getFloat("Yaw"), tag.getFloat("Pitch"), tag.getBoolean("Public"));
    }
}
