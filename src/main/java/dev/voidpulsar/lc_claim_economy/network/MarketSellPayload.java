package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.MarketService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client asks to list the chunk it's currently standing in for sale at {@code priceCopper}. */
public record MarketSellPayload(long priceCopper) implements CustomPacketPayload {
    public static final Type<MarketSellPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "market_sell"));
    public static final StreamCodec<FriendlyByteBuf, MarketSellPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeVarLong(payload.priceCopper),
            buffer -> new MarketSellPayload(buffer.readVarLong())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(MarketSellPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MarketService.list(player.createCommandSourceStack(), payload.priceCopper);
                MarketService.syncToPlayer(player);
            }
        });
    }
}
