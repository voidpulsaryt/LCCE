package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.MarketService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client asks to buy the listing on the chunk it's currently standing in. */
public record MarketBuyPayload() implements CustomPacketPayload {
    public static final Type<MarketBuyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "market_buy"));
    public static final StreamCodec<FriendlyByteBuf, MarketBuyPayload> STREAM_CODEC =
            StreamCodec.unit(new MarketBuyPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(MarketBuyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MarketService.buy(player.createCommandSourceStack());
                MarketService.syncToPlayer(player);
            }
        });
    }
}
