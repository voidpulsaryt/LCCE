package dev.voidpulsaryt.lcce.region;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The independent protection dial for one region: mob griefing/explosions/PvP toggles, plus who's
 * allowed to interact with or edit blocks and entities. Mutable, since players/officers edit this
 * in place through commands.
 */
public final class RegionProtectionSettings {

    private boolean allowMobGriefing;
    private boolean allowExplosions;
    private boolean allowPvp;
    private PermissionTier interactTier;
    private PermissionTier editTier;
    private final Set<UUID> privateWhitelist;

    private RegionProtectionSettings(boolean allowMobGriefing, boolean allowExplosions, boolean allowPvp,
                                       PermissionTier interactTier, PermissionTier editTier, Set<UUID> privateWhitelist) {
        this.allowMobGriefing = allowMobGriefing;
        this.allowExplosions = allowExplosions;
        this.allowPvp = allowPvp;
        this.interactTier = interactTier;
        this.editTier = editTier;
        this.privateWhitelist = privateWhitelist;
    }

    /** Sensible defaults for a freshly created region, or a team's default (unassigned-chunk) settings. */
    public static RegionProtectionSettings defaults() {
        return new RegionProtectionSettings(false, false, false, PermissionTier.ALLIES, PermissionTier.TEAM, new HashSet<>());
    }

    public boolean allowMobGriefing() { return allowMobGriefing; }
    public void setAllowMobGriefing(boolean value) { this.allowMobGriefing = value; }

    public boolean allowExplosions() { return allowExplosions; }
    public void setAllowExplosions(boolean value) { this.allowExplosions = value; }

    public boolean allowPvp() { return allowPvp; }
    public void setAllowPvp(boolean value) { this.allowPvp = value; }

    public PermissionTier interactTier() { return interactTier; }
    public void setInteractTier(PermissionTier tier) { this.interactTier = tier; }

    public PermissionTier editTier() { return editTier; }
    public void setEditTier(PermissionTier tier) { this.editTier = tier; }

    public Set<UUID> privateWhitelist() { return privateWhitelist; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("AllowMobGriefing", allowMobGriefing);
        tag.putBoolean("AllowExplosions", allowExplosions);
        tag.putBoolean("AllowPvp", allowPvp);
        tag.putString("InteractTier", interactTier.name());
        tag.putString("EditTier", editTier.name());
        Tag[] whitelistTags = privateWhitelist.stream().map(NbtUtils::createUUID).toArray(Tag[]::new);
        var list = new net.minecraft.nbt.ListTag();
        for (Tag t : whitelistTags) list.add(t);
        tag.put("PrivateWhitelist", list);
        return tag;
    }

    public static RegionProtectionSettings load(CompoundTag tag) {
        RegionProtectionSettings settings = defaults();
        settings.allowMobGriefing = tag.getBoolean("AllowMobGriefing");
        settings.allowExplosions = tag.getBoolean("AllowExplosions");
        settings.allowPvp = tag.getBoolean("AllowPvp");
        try {
            settings.interactTier = PermissionTier.valueOf(tag.getString("InteractTier"));
            settings.editTier = PermissionTier.valueOf(tag.getString("EditTier"));
        } catch (IllegalArgumentException ignored) {
            // keep defaults if the config value got corrupted somehow
        }
        net.minecraft.nbt.ListTag list = tag.getList("PrivateWhitelist", Tag.TAG_INT_ARRAY);
        for (Tag t : list) {
            settings.privateWhitelist.add(NbtUtils.loadUUID(t));
        }
        return settings;
    }
}
