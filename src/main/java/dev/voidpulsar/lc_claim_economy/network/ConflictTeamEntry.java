package dev.voidpulsar.lc_claim_economy.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * A single row shown in the conflict list UI, describing one other team from the viewing
 * team's point of view — whether that's an incoming war, an outgoing war, or a team the
 * viewer could still declare against.
 *
 * <p>Field order here is the wire format: {@link #STREAM_CODEC} reads and writes the record
 * components positionally, so nothing about the layout below can move without breaking network
 * compatibility. The four protection booleans snapshot that team's live claim safeguards; see
 * {@link #hasWarVulnerability()} for why they matter (a fully-safeguarded team is not worth
 * declaring on, since none of their claims could actually be touched). {@code
 * opponentPendingDeclareOnViewer} is set when the opponent already has a declaration queued
 * against the viewer, letting the client warn before the viewer declares back.
 */
public record ConflictTeamEntry(
        UUID opponentId,
        String opponentName,
        long opponentBaseUpkeepCopper,
        long conflictCostCopper,
        ConflictEntryStatus conflictStatus,
        boolean incomingDeclarePending,
        boolean blockProtectionActive,
        boolean explosionProtectionActive,
        boolean pvpProtectionActive
) {
    /** Shorthand for a freshly-active entry with every safeguard still up and nothing pending. */
    public ConflictTeamEntry(
            UUID opponentId,
            String opponentName,
            long opponentBaseUpkeepCopper,
            long conflictCostCopper
    ) {
        this(
                opponentId,
                opponentName,
                opponentBaseUpkeepCopper,
                conflictCostCopper,
                ConflictEntryStatus.ENGAGED,
                false,
                true,
                true,
                true
        );
    }

    public boolean isPending() {
        return conflictStatus.isPending();
    }

    /** True once at least one of the three claim safeguards has been dropped. */
    public boolean hasWarVulnerability() {
        return !blockProtectionActive || !explosionProtectionActive || !pvpProtectionActive;
    }

    public static final StreamCodec<FriendlyByteBuf, ConflictTeamEntry> STREAM_CODEC =
            StreamCodec.of(ConflictTeamEntry::writeTo, ConflictTeamEntry::readFrom);

    private static void writeTo(FriendlyByteBuf out, ConflictTeamEntry value) {
        out.writeUUID(value.opponentId);
        out.writeUtf(value.opponentName);
        out.writeVarLong(value.opponentBaseUpkeepCopper);
        out.writeVarLong(value.conflictCostCopper);
        out.writeVarInt(value.conflictStatus.id());
        out.writeBoolean(value.incomingDeclarePending);
        out.writeBoolean(value.blockProtectionActive);
        out.writeBoolean(value.explosionProtectionActive);
        out.writeBoolean(value.pvpProtectionActive);
    }

    private static ConflictTeamEntry readFrom(FriendlyByteBuf in) {
        return new ConflictTeamEntry(
                in.readUUID(),
                in.readUtf(),
                in.readVarLong(),
                in.readVarLong(),
                ConflictEntryStatus.fromId(in.readVarInt()),
                in.readBoolean(),
                in.readBoolean(),
                in.readBoolean(),
                in.readBoolean()
        );
    }
}
