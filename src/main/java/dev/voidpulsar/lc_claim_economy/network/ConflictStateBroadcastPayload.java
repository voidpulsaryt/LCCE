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

import java.util.ArrayList;
import java.util.List;

/**
 * Server's reply to {@link ConflictStateRequestPayload}, and re-pushed after any war
 * declaration/end/config change that could move the numbers - the three team lists cover every
 * panel tab (incoming wars against the viewer, the viewer's own outgoing wars, and eligible
 * targets not yet at war) so the client never has to request them separately.
 * {@code warDeclarationWindowDescription} is a pre-formatted string rather than a raw duration
 * so the client doesn't need its own copy of the cooldown-window formatting rules.
 */
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
                writeEntryList(buffer, payload.incoming);
                writeEntryList(buffer, payload.outgoing);
                writeEntryList(buffer, payload.availableTargets);
                buffer.writeBoolean(payload.canManageWar);
                buffer.writeBoolean(payload.warDeclarationWindowOpen);
                buffer.writeUtf(payload.warDeclarationWindowDescription, 256);
            },
            // Argument order below must track the record's declared field order exactly, since
            // each buffer.read*() call has a side effect (advancing the read cursor) - Java
            // guarantees left-to-right evaluation of constructor arguments, so this reads back
            // in the same sequence the lambda above wrote in.
            buffer -> new ConflictStateBroadcastPayload(
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    buffer.readDouble(),
                    readEntryList(buffer),
                    readEntryList(buffer),
                    readEntryList(buffer),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readUtf(256)
            )
    );

    private static void writeEntryList(FriendlyByteBuf buffer, List<ConflictTeamEntry> entries) {
        buffer.writeVarInt(entries.size());
        for (ConflictTeamEntry entry : entries) {
            ConflictTeamEntry.STREAM_CODEC.encode(buffer, entry);
        }
    }

    private static List<ConflictTeamEntry> readEntryList(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<ConflictTeamEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(ConflictTeamEntry.STREAM_CODEC.decode(buffer));
        }
        return entries;
    }

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
