package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.ChunkUserPermissionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client requests a permission-flag change for one entry in a chunk's user permission list.
 * {@code playerRef} is either a raw UUID string or the {@code "*"} sentinel for the catch-all
 * "all players" row; {@code flags} is the full replacement {@code ChunkPermissionFlags} bitmask
 * for that entry, not a delta.
 */
public record SetChunkUserPermsPayload(String chunkKey, String playerRef, int flags) implements CustomPacketPayload {
    public static final Type<SetChunkUserPermsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "set_chunk_user_perms"));
    public static final StreamCodec<FriendlyByteBuf, SetChunkUserPermsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.chunkKey);
                buffer.writeUtf(payload.playerRef);
                buffer.writeVarInt(payload.flags);
            },
            buffer -> new SetChunkUserPermsPayload(buffer.readUtf(), buffer.readUtf(), buffer.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(SetChunkUserPermsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ChunkUserPermissionService.handleSetRequest(player, payload.chunkKey, payload.playerRef, payload.flags);
            }
        });
    }
}
