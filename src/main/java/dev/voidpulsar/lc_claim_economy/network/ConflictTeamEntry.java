package dev.voidpulsar.lc_claim_economy.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * One row in a {@link ConflictStateBroadcastPayload} list (incoming war, outgoing war, or
 * available target) describing the other team from the viewer's perspective. The four
 * protection flags mirror that team's current claim safeguards and drive {@link
 * #hasWarVulnerability()} - a team with every safeguard enabled is a pointless war target since
 * nothing about their claims can actually be affected. {@code opponentPendingDeclareOnViewer}
 * flags that this opponent already has a declare pending against the viewer, so the UI can
 * warn before the viewer declares back. The 4-arg constructor covers the common case of a
 * fresh {@code ACTIVE} entry with every safeguard still enabled and no pending declare against
 * the viewer - callers that need to report an actual protection state use the full constructor.
 */
public record ConflictTeamEntry(
        UUID teamId,
        String displayName,
        long targetBaseUpkeepCopper,
        long warCostCopper,
        ConflictEntryStatus status,
        boolean opponentPendingDeclareOnViewer,
        boolean blockEditProtected,
        boolean explosionProtected,
        boolean pvpProtected
) {
    public ConflictTeamEntry(
            UUID teamId,
            String displayName,
            long targetBaseUpkeepCopper,
            long warCostCopper
    ) {
        this(
                teamId,
                displayName,
                targetBaseUpkeepCopper,
                warCostCopper,
                ConflictEntryStatus.ACTIVE,
                false,
                true,
                true,
                true
        );
    }

    public boolean isPending() {
        return status.isPending();
    }

    public boolean hasWarVulnerability() {
        return !blockEditProtected || !explosionProtected || !pvpProtected;
    }

    public static final StreamCodec<FriendlyByteBuf, ConflictTeamEntry> STREAM_CODEC = StreamCodec.of(
            (buffer, entry) -> {
                buffer.writeUUID(entry.teamId);
                buffer.writeUtf(entry.displayName);
                buffer.writeVarLong(entry.targetBaseUpkeepCopper);
                buffer.writeVarLong(entry.warCostCopper);
                buffer.writeVarInt(entry.status.id());
                buffer.writeBoolean(entry.opponentPendingDeclareOnViewer);
                buffer.writeBoolean(entry.blockEditProtected);
                buffer.writeBoolean(entry.explosionProtected);
                buffer.writeBoolean(entry.pvpProtected);
            },
            buffer -> new ConflictTeamEntry(
                    buffer.readUUID(),
                    buffer.readUtf(),
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    ConflictEntryStatus.fromId(buffer.readVarInt()),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            )
    );
}
