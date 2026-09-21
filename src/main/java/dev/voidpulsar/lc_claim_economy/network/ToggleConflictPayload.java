package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.ConflictService;
import dev.voidpulsar.lc_claim_economy.service.ConflictSyncCoordinator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Client requests declaring or ending a war against {@code targetTeamId}; {@link dev.voidpulsar.lc_claim_economy.service.ConflictService#toggleWar} decides which based on current state. */
public record ToggleConflictPayload(UUID targetTeamId) implements CustomPacketPayload {
    public static final Type<ToggleConflictPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "toggle_war"));
    public static final StreamCodec<FriendlyByteBuf, ToggleConflictPayload> STREAM_CODEC = StreamCodec.of(
            (sink, request) -> sink.writeUUID(request.targetTeamId),
            source -> new ToggleConflictPayload(source.readUUID())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(ToggleConflictPayload request, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer requester)) {
                return;
            }
            var resultMessage = ConflictService.toggleWar(requester.server, requester, request.targetTeamId());
            if (resultMessage != null) {
                requester.displayClientMessage(resultMessage, false);
            }
            ConflictSyncCoordinator.syncToPlayer(requester);
            ConflictSyncCoordinator.syncToTeam(requester.server, request.targetTeamId());
        });
    }
}
