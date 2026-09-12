package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.client.ClientWarps;
import dev.voidpulsar.lc_claim_economy.client.gui.WarpListScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pushed to a player both to open the warp GUI (a bare {@code /lcce warp})
 * and to refresh it after any action (create/delete/toggle public/teleport)
 * - {@link #handleClient} opens the screen if it isn't already open, or just
 * rebuilds it in place if it is, so command-line warp usage (which never
 * triggers this payload) never pops the GUI open unexpectedly.
 */
public record SyncWarpsPayload(
        boolean enabled,
        int maxWarps,
        long createCostCopper,
        long teleportCostCopper,
        List<WarpDto> ownWarps,
        List<WarpDto> publicWarps
) implements CustomPacketPayload {
    public static final Type<SyncWarpsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "sync_warps"));

    public static final StreamCodec<FriendlyByteBuf, SyncWarpsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.enabled);
                buffer.writeVarInt(payload.maxWarps);
                buffer.writeLong(payload.createCostCopper);
                buffer.writeLong(payload.teleportCostCopper);
                writeList(buffer, payload.ownWarps);
                writeList(buffer, payload.publicWarps);
            },
            buffer -> new SyncWarpsPayload(
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readLong(),
                    buffer.readLong(),
                    readList(buffer),
                    readList(buffer)
            )
    );

    private static void writeList(FriendlyByteBuf buffer, List<WarpDto> warps) {
        buffer.writeVarInt(warps.size());
        for (WarpDto warp : warps) {
            buffer.writeUtf(warp.name());
            buffer.writeUUID(warp.ownerId());
            buffer.writeUtf(warp.ownerName());
            buffer.writeUtf(warp.dimensionDisplay());
            buffer.writeVarInt(warp.x());
            buffer.writeVarInt(warp.y());
            buffer.writeVarInt(warp.z());
            buffer.writeBoolean(warp.isPublic());
            buffer.writeVarInt(warp.aliases().size());
            for (String alias : warp.aliases()) {
                buffer.writeUtf(alias);
            }
        }
    }

    private static List<WarpDto> readList(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<WarpDto> warps = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String name = buffer.readUtf();
            UUID ownerId = buffer.readUUID();
            String ownerName = buffer.readUtf();
            String dimensionDisplay = buffer.readUtf();
            int x = buffer.readVarInt();
            int y = buffer.readVarInt();
            int z = buffer.readVarInt();
            boolean isPublic = buffer.readBoolean();
            int aliasCount = buffer.readVarInt();
            List<String> aliases = new ArrayList<>(aliasCount);
            for (int j = 0; j < aliasCount; j++) {
                aliases.add(buffer.readUtf());
            }
            warps.add(new WarpDto(name, ownerId, ownerName, dimensionDisplay, x, y, z, isPublic, aliases));
        }
        return warps;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncWarpsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientWarps.update(payload);
            WarpListScreen.openOrRefresh();
        });
    }
}
