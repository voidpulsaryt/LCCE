package dev.voidpulsaryt.lcce.region;

import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A named subdivision of a team's claimed chunks - Village, Farm, Vault, whatever makes sense -
 * with its own independent {@link RegionProtectionSettings}.
 * <p>
 * {@link #dismantled} tracks which of the region's {@link ProtectionLineItem}s are currently
 * suppressed by the upkeep system because the team couldn't afford them - distinct from
 * {@link #settings}, which is what the team actually <em>wants</em>. Upkeep billing restores a
 * dismantled item automatically the moment the team can pay for it again, without the team having
 * to re-configure anything.
 */
public final class Region {

    private final UUID id;
    private final UUID teamId;
    private String name;
    private final Set<ChunkDimPos> chunks;
    private final RegionProtectionSettings settings;
    private final Set<ProtectionLineItem> dismantled;

    public Region(UUID id, UUID teamId, String name, Set<ChunkDimPos> chunks, RegionProtectionSettings settings,
                  Set<ProtectionLineItem> dismantled) {
        this.id = id;
        this.teamId = teamId;
        this.name = name;
        this.chunks = chunks;
        this.settings = settings;
        this.dismantled = dismantled;
    }

    public static Region create(UUID teamId, String name) {
        return new Region(UUID.randomUUID(), teamId, name, new HashSet<>(), RegionProtectionSettings.defaults(),
                EnumSet.noneOf(ProtectionLineItem.class));
    }

    public UUID id() { return id; }
    public UUID teamId() { return teamId; }
    public String name() { return name; }
    public void rename(String name) { this.name = name; }
    public Set<ChunkDimPos> chunks() { return chunks; }
    public RegionProtectionSettings settings() { return settings; }
    public Set<ProtectionLineItem> dismantled() { return dismantled; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("TeamId", teamId);
        tag.putString("Name", name);
        tag.put("Settings", settings.save());

        ListTag chunkList = new ListTag();
        for (ChunkDimPos pos : chunks) {
            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putString("Dim", pos.dimension().location().toString());
            chunkTag.putInt("X", pos.x());
            chunkTag.putInt("Z", pos.z());
            chunkList.add(chunkTag);
        }
        tag.put("Chunks", chunkList);

        ListTag dismantledList = new ListTag();
        for (ProtectionLineItem item : dismantled) {
            dismantledList.add(StringTag.valueOf(item.name()));
        }
        tag.put("Dismantled", dismantledList);

        return tag;
    }

    public static Region load(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        UUID teamId = tag.getUUID("TeamId");
        String name = tag.getString("Name");
        RegionProtectionSettings settings = RegionProtectionSettings.load(tag.getCompound("Settings"));

        Set<ChunkDimPos> chunks = new HashSet<>();
        ListTag chunkList = tag.getList("Chunks", Tag.TAG_COMPOUND);
        for (Tag t : chunkList) {
            CompoundTag chunkTag = (CompoundTag) t;
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.parse(chunkTag.getString("Dim")));
            chunks.add(new ChunkDimPos(dim, new ChunkPos(chunkTag.getInt("X"), chunkTag.getInt("Z"))));
        }

        Set<ProtectionLineItem> dismantled = EnumSet.noneOf(ProtectionLineItem.class);
        ListTag dismantledList = tag.getList("Dismantled", Tag.TAG_STRING);
        for (Tag t : dismantledList) {
            try {
                dismantled.add(ProtectionLineItem.valueOf(t.getAsString()));
            } catch (IllegalArgumentException ignored) {
                // stale/unknown line item name, safe to skip
            }
        }

        return new Region(id, teamId, name, chunks, settings, dismantled);
    }
}
