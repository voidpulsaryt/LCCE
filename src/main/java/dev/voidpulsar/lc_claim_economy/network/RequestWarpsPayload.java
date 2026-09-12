package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.WarpService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The warp GUI's Refresh button; the bare {@code /lcce warp} command sends the same data directly instead of via this. */
public record RequestWarpsPayload() implements CustomPacketPayload {
    public static final Type<RequestWarpsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "request_warps"));
    public static final StreamCodec<FriendlyByteBuf, RequestWarpsPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestWarpsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(RequestWarpsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WarpService.syncToPlayer(player);
            }
        });
    }
}
