package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.ChunkUserPermissionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client asks the server to (re)send the permission list for one chunk - sent when {@code ChunkUserPermissionsScreen} opens or is manually refreshed. */
public record RequestChunkUserPermsPayload(String chunkKey) implements CustomPacketPayload {
    public static final Type<RequestChunkUserPermsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "request_chunk_user_perms"));
    public static final StreamCodec<FriendlyByteBuf, RequestChunkUserPermsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeUtf(payload.chunkKey),
            buffer -> new RequestChunkUserPermsPayload(buffer.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(RequestChunkUserPermsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ChunkUserPermissionService.syncToPlayer(player, payload.chunkKey);
            }
        });
    }
}
