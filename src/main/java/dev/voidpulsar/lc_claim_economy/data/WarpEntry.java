package dev.voidpulsar.lc_claim_economy.data;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.UUID;

/**
 * A player-owned warp: a named teleport point set inside a chunk the owner's
 * own team has claimed (when {@code warpRequireOwnClaim} is enabled).
 * {@link #chunkKey} is kept alongside the raw coordinates so {@link
 * dev.voidpulsar.lc_claim_economy.service.LandChunkService#onChunkUnclaimed}
 * can delete any warps sitting on a chunk once it's no longer claimed.
 * {@link #aliases} are extra lowercase names that resolve to this same warp
 * (see {@link LcClaimEconomySavedData#resolveWarp}) - unique only within
 * this warp's own owner, never globally.
 */
public record WarpEntry(
        String name,
        UUID ownerId,
        String ownerName,
        ResourceLocation dimension,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String chunkKey,
        boolean isPublic,
        long createdAtMillis,
        Set<String> aliases
) {
    public WarpEntry withPublic(boolean pub) {
        return new WarpEntry(name, ownerId, ownerName, dimension, x, y, z, yaw, pitch, chunkKey, pub, createdAtMillis, aliases);
    }

    public WarpEntry withAliases(Set<String> newAliases) {
        return new WarpEntry(name, ownerId, ownerName, dimension, x, y, z, yaw, pitch, chunkKey, isPublic, createdAtMillis, newAliases);
    }
}
