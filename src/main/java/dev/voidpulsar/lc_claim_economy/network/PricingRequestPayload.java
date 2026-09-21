package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.ClaimPricingBroadcast;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Empty request - client asks the server to resend claim/upkeep/protection pricing plus the requester's current balance and chunk counts. */
public record PricingRequestPayload() implements CustomPacketPayload {
    public static final Type<PricingRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "request_claim_prices"));
    public static final StreamCodec<FriendlyByteBuf, PricingRequestPayload> STREAM_CODEC = StreamCodec.unit(new PricingRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(PricingRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ClaimPricingBroadcast.syncToPlayer(player);
            }
        });
    }
}
