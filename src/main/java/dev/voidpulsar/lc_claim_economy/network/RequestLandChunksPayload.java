package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.LandChunkService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Empty request - client asks the server to resend the full set of chunks flagged "land" (vs "build") for the claim-type toggle UI. */
public record RequestLandChunksPayload() implements CustomPacketPayload {
    public static final Type<RequestLandChunksPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "request_land_chunks"));
    public static final StreamCodec<FriendlyByteBuf, RequestLandChunksPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestLandChunksPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(RequestLandChunksPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                LandChunkService.syncToPlayer(player);
            }
        });
    }
}
