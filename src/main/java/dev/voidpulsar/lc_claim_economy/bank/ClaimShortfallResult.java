package dev.voidpulsar.lc_claim_economy.bank;

import dev.ftb.mods.ftbchunks.api.ClaimResult;
import net.minecraft.network.chat.MutableComponent;

/** {@link dev.ftb.mods.ftbchunks.api.ClaimResult} for a single failed claim attempt - carries the pre-built "you can't afford this" message so FTB Chunks' own claim-result handling can display it without knowing anything about this mod's pricing. */
public record ClaimShortfallResult(MutableComponent shortfallMessage) implements ClaimResult {
    public static final String RESULT_ID = "message.lc_claim_economy.insufficient_funds_summary";

    @Override
    public String getResultId() {
        return RESULT_ID;
    }

    @Override
    public MutableComponent getMessage() {
        return shortfallMessage;
    }
}
