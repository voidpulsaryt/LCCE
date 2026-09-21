package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.ConflictSyncCoordinator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Empty request - client asks the server to resend the full war/conflict panel state for the requester's team. */
public record ConflictStateRequestPayload() implements CustomPacketPayload {
    public static final Type<ConflictStateRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "request_war_state"));
    public static final StreamCodec<FriendlyByteBuf, ConflictStateRequestPayload> STREAM_CODEC = StreamCodec.unit(new ConflictStateRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(ConflictStateRequestPayload received, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer requester) {
                ConflictSyncCoordinator.syncToPlayer(requester);
            }
        });
    }
}
