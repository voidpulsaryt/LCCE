package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.MarketService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client asks for a fresh {@link SyncMarketPayload} - sent on opening the market GUI and by its refresh button. */
public record RequestMarketPayload() implements CustomPacketPayload {
    public static final Type<RequestMarketPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "request_market"));
    public static final StreamCodec<FriendlyByteBuf, RequestMarketPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestMarketPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(RequestMarketPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MarketService.syncToPlayer(player);
            }
        });
    }
}
