package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.WarpService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record WarpSetPublicPayload(String name, boolean isPublic) implements CustomPacketPayload {
    public static final Type<WarpSetPublicPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "warp_set_public"));
    public static final StreamCodec<FriendlyByteBuf, WarpSetPublicPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.name);
                buffer.writeBoolean(payload.isPublic);
            },
            buffer -> new WarpSetPublicPayload(buffer.readUtf(), buffer.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(WarpSetPublicPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WarpService.setPublic(player, payload.name, payload.isPublic);
                WarpService.syncToPlayer(player);
            }
        });
    }
}
