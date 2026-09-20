package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.client.ClientConflictState;
import dev.voidpulsar.lc_claim_economy.client.gui.ConflictScreen;
import dev.voidpulsar.lc_claim_economy.client.QueuedStateUiRefresh;
import dev.ftb.mods.ftblibrary.util.client.ClientUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record ConflictStateBroadcastPayload(
        long baseUpkeepCopper,
        long incomingWarCopper,
        long outgoingWarCopper,
        double warCostMultiplier,
        List<ConflictTeamEntry> incoming,
        List<ConflictTeamEntry> outgoing,
        List<ConflictTeamEntry> availableTargets,
        boolean canManageWar,
        boolean warDeclarationWindowOpen,
        String warDeclarationWindowDescription
) implements CustomPacketPayload {
    public static final Type<ConflictStateBroadcastPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "sync_war_state"));
    public static final StreamCodec<FriendlyByteBuf, ConflictStateBroadcastPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarLong(payload.baseUpkeepCopper);
                buffer.writeVarLong(payload.incomingWarCopper);
                buffer.writeVarLong(payload.outgoingWarCopper);
                buffer.writeDouble(payload.warCostMultiplier);
                buffer.writeVarInt(payload.incoming.size());
                for (ConflictTeamEntry entry : payload.incoming) {
                    ConflictTeamEntry.STREAM_CODEC.encode(buffer, entry);
                }
                buffer.writeVarInt(payload.outgoing.size());
                for (ConflictTeamEntry entry : payload.outgoing) {
                    ConflictTeamEntry.STREAM_CODEC.encode(buffer, entry);
                }
                buffer.writeVarInt(payload.availableTargets.size());
                for (ConflictTeamEntry entry : payload.availableTargets) {
                    ConflictTeamEntry.STREAM_CODEC.encode(buffer, entry);
                }
                buffer.writeBoolean(payload.canManageWar);
                buffer.writeBoolean(payload.warDeclarationWindowOpen);
                buffer.writeUtf(payload.warDeclarationWindowDescription, 256);
            },
            buffer -> {
                long base = buffer.readVarLong();
                long incoming = buffer.readVarLong();
                long outgoing = buffer.readVarLong();
                double multiplier = buffer.readDouble();
                int incomingCount = buffer.readVarInt();
                List<ConflictTeamEntry> incomingEntries = new java.util.ArrayList<>(incomingCount);
                for (int i = 0; i < incomingCount; i++) {
                    incomingEntries.add(ConflictTeamEntry.STREAM_CODEC.decode(buffer));
                }
                int outgoingCount = buffer.readVarInt();
                List<ConflictTeamEntry> outgoingEntries = new java.util.ArrayList<>(outgoingCount);
                for (int i = 0; i < outgoingCount; i++) {
                    outgoingEntries.add(ConflictTeamEntry.STREAM_CODEC.decode(buffer));
                }
                int targetCount = buffer.readVarInt();
                List<ConflictTeamEntry> targets = new java.util.ArrayList<>(targetCount);
                for (int i = 0; i < targetCount; i++) {
                    targets.add(ConflictTeamEntry.STREAM_CODEC.decode(buffer));
                }
                return new ConflictStateBroadcastPayload(
                        base,
                        incoming,
                        outgoing,
                        multiplier,
                        incomingEntries,
                        outgoingEntries,
                        targets,
                        buffer.readBoolean(),
                        buffer.readBoolean(),
                        buffer.readUtf(256)
                );
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(ConflictStateBroadcastPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientConflictState.update(payload);
            if (ClientUtils.getCurrentGuiAs(ConflictScreen.class) != null) {
                ConflictScreen.refreshIfOpen();
            } else {
                QueuedStateUiRefresh.refreshOpenScreens();
            }
        });
    }
}
