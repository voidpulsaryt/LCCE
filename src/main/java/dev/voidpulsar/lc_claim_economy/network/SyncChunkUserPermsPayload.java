package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.client.ClientChunkUserPermissions;
import dev.voidpulsar.lc_claim_economy.client.gui.ChunkUserPermissionsScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server's reply to {@link RequestChunkUserPermsPayload} (or a push after {@link
 * SetChunkUserPermsPayload} is applied): the full permission list for one chunk, plus whether
 * the recipient is allowed to edit it - {@code canManage} gates whether the client renders the
 * list read-only or with edit controls, since a non-manager can still open the screen to view it.
 */
public record SyncChunkUserPermsPayload(String chunkKey, boolean canManage, List<ChunkUserPermissionEntry> entries)
        implements CustomPacketPayload {
    public static final Type<SyncChunkUserPermsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "sync_chunk_user_perms"));

    public static final StreamCodec<FriendlyByteBuf, SyncChunkUserPermsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUtf(payload.chunkKey);
                buffer.writeBoolean(payload.canManage);
                buffer.writeVarInt(payload.entries.size());
                for (ChunkUserPermissionEntry entry : payload.entries) {
                    buffer.writeUUID(entry.playerId());
                    buffer.writeUtf(entry.displayName());
                    buffer.writeVarInt(entry.flags());
                    buffer.writeBoolean(entry.allPlayers());
                }
            },
            buffer -> {
                String chunkKey = buffer.readUtf();
                boolean canManage = buffer.readBoolean();
                int size = buffer.readVarInt();
                List<ChunkUserPermissionEntry> entries = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    UUID id = buffer.readUUID();
                    String name = buffer.readUtf();
                    int flags = buffer.readVarInt();
                    boolean allPlayers = buffer.readBoolean();
                    entries.add(new ChunkUserPermissionEntry(id, name, flags, allPlayers));
                }
                return new SyncChunkUserPermsPayload(chunkKey, canManage, entries);
            }
    );

    public static SyncChunkUserPermsPayload empty(String chunkKey) {
        return new SyncChunkUserPermsPayload(chunkKey, false, List.of());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncChunkUserPermsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientChunkUserPermissions.update(payload.chunkKey, payload.canManage, payload.entries);
            ChunkUserPermissionsScreen.refreshIfOpen();
        });
    }
}
