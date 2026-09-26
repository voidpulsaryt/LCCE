package dev.voidpulsaryt.lcce.network;

import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.voidpulsaryt.lcce.LCCEMod;
import dev.voidpulsaryt.lcce.region.PermissionTier;
import dev.voidpulsaryt.lcce.region.Region;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server -> a team's own online members only: every one of that team's {@link Region}s, its
 * protection settings, and the chunks in it. Region membership (unlike claimed-chunk data, which
 * FTB Chunks already syncs to every client via {@code ChunksUpdatedFromServerEvent}) is entirely
 * this mod's own data and was never sent to any client before this - needed for the World Map's
 * nested region-border highlighting and the region-info popup, same underlying gap
 * {@code TeamBalanceSyncPayload} closed for team balance. Sent whenever a team's regions change
 * (region create/delete, a chunk added to/removed from a region, protection settings edited) and
 * once on login - always the *entire* current set of regions for that team, since regions are a
 * low-frequency admin action, not something worth a more granular incremental sync over.
 */
public record RegionSyncPayload(UUID teamId, List<RegionEntry> regions) implements CustomPacketPayload {

    public static final Type<RegionSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCCEMod.MOD_ID, "region_sync"));

    public record RegionEntry(UUID regionId, String name, boolean mobGriefing, boolean explosions, boolean pvp,
                               PermissionTier interactTier, PermissionTier editTier, List<ChunkDimPos> chunks) {
        public static RegionEntry fromRegion(Region region) {
            return new RegionEntry(
                    region.id(), region.name(),
                    region.settings().allowMobGriefing(), region.settings().allowExplosions(), region.settings().allowPvp(),
                    region.settings().interactTier(), region.settings().editTier(),
                    List.copyOf(region.chunks())
            );
        }
    }

    public static final StreamCodec<FriendlyByteBuf, RegionSyncPayload> STREAM_CODEC = StreamCodec.of(
            RegionSyncPayload::write, RegionSyncPayload::read
    );

    private static void write(FriendlyByteBuf buf, RegionSyncPayload payload) {
        buf.writeUUID(payload.teamId());
        buf.writeVarInt(payload.regions().size());
        for (RegionEntry entry : payload.regions()) {
            buf.writeUUID(entry.regionId());
            buf.writeUtf(entry.name());
            buf.writeBoolean(entry.mobGriefing());
            buf.writeBoolean(entry.explosions());
            buf.writeBoolean(entry.pvp());
            buf.writeEnum(entry.interactTier());
            buf.writeEnum(entry.editTier());
            buf.writeVarInt(entry.chunks().size());
            for (ChunkDimPos pos : entry.chunks()) {
                buf.writeResourceLocation(pos.dimension().location());
                buf.writeVarInt(pos.x());
                buf.writeVarInt(pos.z());
            }
        }
    }

    private static RegionSyncPayload read(FriendlyByteBuf buf) {
        UUID teamId = buf.readUUID();
        int regionCount = buf.readVarInt();
        List<RegionEntry> regions = new ArrayList<>(regionCount);
        for (int i = 0; i < regionCount; i++) {
            UUID regionId = buf.readUUID();
            String name = buf.readUtf();
            boolean mobGriefing = buf.readBoolean();
            boolean explosions = buf.readBoolean();
            boolean pvp = buf.readBoolean();
            PermissionTier interactTier = buf.readEnum(PermissionTier.class);
            PermissionTier editTier = buf.readEnum(PermissionTier.class);
            int chunkCount = buf.readVarInt();
            List<ChunkDimPos> chunks = new ArrayList<>(chunkCount);
            for (int c = 0; c < chunkCount; c++) {
                ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, buf.readResourceLocation());
                int x = buf.readVarInt();
                int z = buf.readVarInt();
                chunks.add(new ChunkDimPos(dim, new ChunkPos(x, z)));
            }
            regions.add(new RegionEntry(regionId, name, mobGriefing, explosions, pvp, interactTier, editTier, chunks));
        }
        return new RegionSyncPayload(teamId, regions);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
