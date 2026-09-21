package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.MarketService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client asks to cancel its own team's listing on the chunk it's currently standing in. */
public record MarketCancelPayload() implements CustomPacketPayload {
    public static final Type<MarketCancelPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "market_cancel"));
    public static final StreamCodec<FriendlyByteBuf, MarketCancelPayload> STREAM_CODEC =
            StreamCodec.unit(new MarketCancelPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(MarketCancelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MarketService.cancel(player.createCommandSourceStack());
                MarketService.syncToPlayer(player);
            }
        });
    }
}
