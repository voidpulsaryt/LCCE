package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.MarketService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent by the claim map screen on open, to populate its "for sale" chunk overlay without triggering the market GUI. */
public record RequestMarketListingsPayload() implements CustomPacketPayload {
    public static final Type<RequestMarketListingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "request_market_listings"));
    public static final StreamCodec<FriendlyByteBuf, RequestMarketListingsPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestMarketListingsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(RequestMarketListingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MarketService.syncListingsOnlyToPlayer(player);
            }
        });
    }
}
