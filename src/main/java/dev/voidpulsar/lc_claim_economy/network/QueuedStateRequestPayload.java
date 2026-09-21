package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Empty request - client asks the server to resend the requester's team's queued (not-yet-applied) property/force-load/chunk-type changes. */
public record QueuedStateRequestPayload() implements CustomPacketPayload {
    public static final Type<QueuedStateRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "request_pending_state"));
    public static final StreamCodec<FriendlyByteBuf, QueuedStateRequestPayload> STREAM_CODEC = StreamCodec.unit(new QueuedStateRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(QueuedStateRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                QueuedStateBroadcast.syncToPlayer(player);
            }
        });
    }
}
