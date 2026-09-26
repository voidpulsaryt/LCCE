package dev.voidpulsaryt.lcce.network;

import dev.ftb.mods.ftblibrary.math.XZ;
import dev.voidpulsaryt.lcce.LCCEMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Client -> server: "make (or add to, if it already exists) a region named {@code name} out of
 * these chunks", sent by the World Map's "New Region from Selection..." right-click option for
 * whatever's currently drag-selected. Chunks aren't tagged with a dimension - like FTB Chunks'
 * own {@code RequestChunkChangePacket}, the server resolves them against the sending player's own
 * current level, and silently skips any chunk that isn't actually claimed by the player's team
 * (never trusting the client's selection alone, since a region should only ever contain a team's
 * own claims).
 */
public record CreateRegionFromSelectionPayload(String name, Set<XZ> chunks) implements CustomPacketPayload {

    public static final Type<CreateRegionFromSelectionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCCEMod.MOD_ID, "create_region_from_selection"));

    public static final StreamCodec<FriendlyByteBuf, CreateRegionFromSelectionPayload> STREAM_CODEC = StreamCodec.of(
            CreateRegionFromSelectionPayload::write, CreateRegionFromSelectionPayload::read
    );

    private static void write(FriendlyByteBuf buf, CreateRegionFromSelectionPayload payload) {
        buf.writeUtf(payload.name());
        buf.writeVarInt(payload.chunks().size());
        for (XZ xz : payload.chunks()) {
            buf.writeVarInt(xz.x());
            buf.writeVarInt(xz.z());
        }
    }

    private static CreateRegionFromSelectionPayload read(FriendlyByteBuf buf) {
        String name = buf.readUtf();
        int count = buf.readVarInt();
        List<XZ> chunks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            chunks.add(XZ.of(buf.readVarInt(), buf.readVarInt()));
        }
        return new CreateRegionFromSelectionPayload(name, Set.copyOf(chunks));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
