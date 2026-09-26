package dev.voidpulsaryt.lcce.network;

import dev.voidpulsaryt.lcce.LCCEMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: "claim the chunk I'm standing in", sent by the map claim keybind. */
public record ClaimChunkPayload() implements CustomPacketPayload {

    public static final Type<ClaimChunkPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCCEMod.MOD_ID, "claim_chunk"));

    public static final StreamCodec<ByteBuf, ClaimChunkPayload> STREAM_CODEC =
            StreamCodec.unit(new ClaimChunkPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
