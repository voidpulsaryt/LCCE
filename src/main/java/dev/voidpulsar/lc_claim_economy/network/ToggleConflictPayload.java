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

public record ToggleConflictPayload(UUID targetTeamId) implements CustomPacketPayload {
    public static final Type<ToggleConflictPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "toggle_war"));
    public static final StreamCodec<FriendlyByteBuf, ToggleConflictPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUUID(payload.targetTeamId),
            buffer -> new ToggleConflictPayload(buffer.readUUID())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(ToggleConflictPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            var message = ConflictService.toggleWar(player.server, player, payload.targetTeamId());
            if (message != null) {
                player.displayClientMessage(message, false);
            }
            ConflictSyncCoordinator.syncToPlayer(player);
            ConflictSyncCoordinator.syncToTeam(player.server, payload.targetTeamId());
        });
    }
}
