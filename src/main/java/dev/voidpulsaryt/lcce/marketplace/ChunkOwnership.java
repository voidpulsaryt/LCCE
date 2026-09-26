package dev.voidpulsaryt.lcce.marketplace;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A chunk that's been bought out of a team's hands into a private player's - the chunk stays
 * claimed by the team as far as FTB Chunks is concerned (it still counts toward their claim limit
 * and stays chunk-loaded the same way), but day-to-day access is entirely up to the private owner's
 * own whitelist/blacklist, overriding whatever the team or its regions would otherwise allow.
 */
public final class ChunkOwnership {

    private UUID ownerId;
    private AccessListMode accessMode;
    private final Set<UUID> accessList;
    @Nullable
    private String customLabel;
    private long lastOwnershipChangeTime;

    public ChunkOwnership(UUID ownerId, AccessListMode accessMode, Set<UUID> accessList,
                           @Nullable String customLabel, long lastOwnershipChangeTime) {
        this.ownerId = ownerId;
        this.accessMode = accessMode;
        this.accessList = accessList;
        this.customLabel = customLabel;
        this.lastOwnershipChangeTime = lastOwnershipChangeTime;
    }

    public static ChunkOwnership boughtBy(UUID ownerId, long now) {
        return new ChunkOwnership(ownerId, AccessListMode.WHITELIST, new HashSet<>(), null, now);
    }

    public UUID ownerId() { return ownerId; }

    public void transferTo(UUID newOwnerId, long now) {
        this.ownerId = newOwnerId;
        this.lastOwnershipChangeTime = now;
    }

    public AccessListMode accessMode() { return accessMode; }
    public void setAccessMode(AccessListMode mode) { this.accessMode = mode; }

    public Set<UUID> accessList() { return accessList; }

    @Nullable
    public String customLabel() { return customLabel; }
    public void setCustomLabel(@Nullable String label) { this.customLabel = label; }

    public long lastOwnershipChangeTime() { return lastOwnershipChangeTime; }

    public boolean isAllowed(UUID playerId) {
        return playerId.equals(ownerId) || accessMode.isAllowed(accessList, playerId);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Owner", ownerId);
        tag.putString("AccessMode", accessMode.name());
        if (customLabel != null) {
            tag.putString("CustomLabel", customLabel);
        }
        tag.putLong("LastChange", lastOwnershipChangeTime);

        ListTag list = new ListTag();
        for (UUID id : accessList) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put("AccessList", list);
        return tag;
    }

    public static ChunkOwnership load(CompoundTag tag) {
        UUID owner = tag.getUUID("Owner");
        AccessListMode mode = AccessListMode.WHITELIST;
        try {
            mode = AccessListMode.valueOf(tag.getString("AccessMode"));
        } catch (IllegalArgumentException ignored) {
            // keep default
        }
        String label = tag.contains("CustomLabel") ? tag.getString("CustomLabel") : null;
        long lastChange = tag.getLong("LastChange");

        Set<UUID> accessList = new HashSet<>();
        ListTag list = tag.getList("AccessList", Tag.TAG_INT_ARRAY);
        for (Tag t : list) {
            accessList.add(NbtUtils.loadUUID(t));
        }

        return new ChunkOwnership(owner, mode, accessList, label, lastChange);
    }
}
