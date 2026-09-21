package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.client.ClientQueuedChanges;
import dev.voidpulsar.lc_claim_economy.client.QueuedStateUiRefresh;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server's reply to {@link QueuedStateRequestPayload}, and re-pushed whenever a team's queued
 * (not-yet-applied) changes are edited - covers everything the pending-changes banner in the
 * claim GUIs needs to render: renamed/changed properties waiting on their cooldown, chunks
 * queued to force-load or force-unload, and chunks queued for a land/build type flip.
 * {@link #EMPTY} is sent to players with no team so the client always has something to render.
 */
public record QueuedStateBroadcastPayload(
        Map<String, String> queuedPropertyEdits,
        Set<String> queuedForceLoadKeys,
        Set<String> queuedForceUnloadKeys,
        Set<String> queuedLandChunkKeys,
        Set<String> queuedBuildChunkKeys
) implements CustomPacketPayload {
    public static final Type<QueuedStateBroadcastPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "sync_pending_state"));
    public static final QueuedStateBroadcastPayload EMPTY = new QueuedStateBroadcastPayload(Map.of(), Set.of(), Set.of(), Set.of(), Set.of());
    // Wire order below must stay exactly as-is (properties, force-loads, force-unloads, land, build) -
    // it is the on-the-wire packet layout, independent of the record component names above.
    public static final StreamCodec<FriendlyByteBuf, QueuedStateBroadcastPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                writePropertyMap(buffer, payload.queuedPropertyEdits);
                buffer.writeCollection(payload.queuedForceLoadKeys, FriendlyByteBuf::writeUtf);
                buffer.writeCollection(payload.queuedForceUnloadKeys, FriendlyByteBuf::writeUtf);
                buffer.writeCollection(payload.queuedLandChunkKeys, FriendlyByteBuf::writeUtf);
                buffer.writeCollection(payload.queuedBuildChunkKeys, FriendlyByteBuf::writeUtf);
            },
            buffer -> new QueuedStateBroadcastPayload(
                    readPropertyMap(buffer),
                    buffer.readCollection(HashSet::new, FriendlyByteBuf::readUtf),
                    buffer.readCollection(HashSet::new, FriendlyByteBuf::readUtf),
                    buffer.readCollection(HashSet::new, FriendlyByteBuf::readUtf),
                    buffer.readCollection(HashSet::new, FriendlyByteBuf::readUtf)
            )
    );

    private static void writePropertyMap(FriendlyByteBuf buffer, Map<String, String> properties) {
        buffer.writeVarInt(properties.size());
        for (var entry : properties.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    private static Map<String, String> readPropertyMap(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        Map<String, String> properties = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            properties.put(buffer.readUtf(), buffer.readUtf());
        }
        return properties;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(QueuedStateBroadcastPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> applyToClient(payload));
    }

    private static void applyToClient(QueuedStateBroadcastPayload payload) {
        LcClaimEconomy.LOGGER.debug("Client received pending state: properties={}, forceLoads={}, forceUnloads={}, landChunks={}, buildChunks={}",
                payload.queuedPropertyEdits, payload.queuedForceLoadKeys, payload.queuedForceUnloadKeys,
                payload.queuedLandChunkKeys, payload.queuedBuildChunkKeys);
        ClientQueuedChanges.update(
                payload.queuedPropertyEdits,
                payload.queuedForceLoadKeys,
                payload.queuedForceUnloadKeys,
                payload.queuedLandChunkKeys,
                payload.queuedBuildChunkKeys
        );
        refreshClientScreens();
    }

    private static void refreshClientScreens() {
        QueuedStateUiRefresh.syncSelfTeamOpenScreen();
        QueuedStateUiRefresh.refreshOpenScreens();
    }
}
