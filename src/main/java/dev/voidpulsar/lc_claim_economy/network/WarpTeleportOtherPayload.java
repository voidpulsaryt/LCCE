package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.WarpService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public record WarpTeleportOtherPayload(UUID ownerId, String name) implements CustomPacketPayload {
    public static final Type<WarpTeleportOtherPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "warp_teleport_other"));
    public static final StreamCodec<FriendlyByteBuf, WarpTeleportOtherPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.ownerId);
                buffer.writeUtf(payload.name);
            },
            buffer -> new WarpTeleportOtherPayload(buffer.readUUID(), buffer.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(WarpTeleportOtherPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WarpService.teleportToOtherWarp(player, payload.ownerId, payload.name);
            }
        });
    }
}
