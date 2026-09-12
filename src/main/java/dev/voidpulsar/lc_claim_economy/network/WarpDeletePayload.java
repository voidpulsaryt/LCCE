package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.WarpService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WarpDeletePayload(String name) implements CustomPacketPayload {
    public static final Type<WarpDeletePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "warp_delete"));
    public static final StreamCodec<FriendlyByteBuf, WarpDeletePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUtf(payload.name),
            buffer -> new WarpDeletePayload(buffer.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(WarpDeletePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WarpService.deleteWarp(player, payload.name);
                WarpService.syncToPlayer(player);
            }
        });
    }
}
