package dev.voidpulsar.lc_claim_economy.bank;

import dev.ftb.mods.ftbchunks.api.ClaimResult;
import net.minecraft.network.chat.MutableComponent;

public final class ClaimShortfallResult implements ClaimResult {
    public static final String RESULT_ID = "message.lc_claim_economy.insufficient_funds_summary";

    private final MutableComponent message;

    public ClaimShortfallResult(MutableComponent message) {
        this.message = message;
    }

    @Override
    public String getResultId() {
        return RESULT_ID;
    }

    @Override
    public MutableComponent getMessage() {
        return message;
    }
}
