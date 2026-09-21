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

public record QueuedStateBroadcastPayload(
        Map<String, String> pendingProperties,
        Set<String> pendingForceLoads,
        Set<String> pendingForceUnloads,
        Set<String> pendingLandChunks,
        Set<String> pendingBuildChunks
) implements CustomPacketPayload {
    public static final Type<QueuedStateBroadcastPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "sync_pending_state"));
    public static final QueuedStateBroadcastPayload EMPTY = new QueuedStateBroadcastPayload(Map.of(), Set.of(), Set.of(), Set.of(), Set.of());
    public static final StreamCodec<FriendlyByteBuf, QueuedStateBroadcastPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.pendingProperties.size());
                for (var entry : payload.pendingProperties.entrySet()) {
                    buffer.writeUtf(entry.getKey());
                    buffer.writeUtf(entry.getValue());
                }
                buffer.writeCollection(payload.pendingForceLoads, FriendlyByteBuf::writeUtf);
                buffer.writeCollection(payload.pendingForceUnloads, FriendlyByteBuf::writeUtf);
                buffer.writeCollection(payload.pendingLandChunks, FriendlyByteBuf::writeUtf);
                buffer.writeCollection(payload.pendingBuildChunks, FriendlyByteBuf::writeUtf);
            },
            buffer -> {
                int propertyCount = buffer.readVarInt();
                Map<String, String> properties = new HashMap<>(propertyCount);
                for (int i = 0; i < propertyCount; i++) {
                    properties.put(buffer.readUtf(), buffer.readUtf());
                }
                return new QueuedStateBroadcastPayload(
                        properties,
                        buffer.readCollection(HashSet::new, FriendlyByteBuf::readUtf),
                        buffer.readCollection(HashSet::new, FriendlyByteBuf::readUtf),
                        buffer.readCollection(HashSet::new, FriendlyByteBuf::readUtf),
                        buffer.readCollection(HashSet::new, FriendlyByteBuf::readUtf)
                );
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(QueuedStateBroadcastPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LcClaimEconomy.LOGGER.debug("Client received pending state: properties={}, forceLoads={}, forceUnloads={}, landChunks={}, buildChunks={}",
                    payload.pendingProperties, payload.pendingForceLoads, payload.pendingForceUnloads,
                    payload.pendingLandChunks, payload.pendingBuildChunks);
            ClientQueuedChanges.update(
                    payload.pendingProperties,
                    payload.pendingForceLoads,
                    payload.pendingForceUnloads,
                    payload.pendingLandChunks,
                    payload.pendingBuildChunks
            );
            QueuedStateUiRefresh.syncSelfTeamOpenScreen();
            QueuedStateUiRefresh.refreshOpenScreens();
        });
    }
}
