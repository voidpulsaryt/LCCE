package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.client.ClientLandChunks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

/** Full set of the recipient's claimed chunks currently flagged as "land" type, keyed by {@code ChunkCoordKey}; anything claimed but absent from this set is a "build" chunk. */
public record SyncLandChunksPayload(Set<String> landChunkKeys) implements CustomPacketPayload {
    public static final Type<SyncLandChunksPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "sync_land_chunks"));
    public static final StreamCodec<FriendlyByteBuf, SyncLandChunksPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeCollection(payload.landChunkKeys, FriendlyByteBuf::writeUtf),
            buffer -> new SyncLandChunksPayload(buffer.readCollection(HashSet::new, FriendlyByteBuf::readUtf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncLandChunksPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientLandChunks.update(payload.landChunkKeys));
    }
}
