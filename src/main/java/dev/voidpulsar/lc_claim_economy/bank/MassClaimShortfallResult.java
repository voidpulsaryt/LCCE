package dev.voidpulsar.lc_claim_economy.bank;

import dev.ftb.mods.ftbchunks.api.ClaimResult;
import net.minecraft.network.chat.MutableComponent;

/** Same role as {@link ClaimShortfallResult} but for the bulk/mass-claim command path, where the message summarizes how many chunks in the batch couldn't be paid for rather than a single failure. */
public record MassClaimShortfallResult(MutableComponent batchShortfallMessage) implements ClaimResult {
    public static final String RESULT_ID = "message.lc_claim_economy.insufficient_funds_bulk_summary";

    @Override
    public String getResultId() {
        return RESULT_ID;
    }

    @Override
    public MutableComponent getMessage() {
        return batchShortfallMessage;
    }
}
