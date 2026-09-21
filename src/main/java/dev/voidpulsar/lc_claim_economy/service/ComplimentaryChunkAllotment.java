package dev.voidpulsar.lc_claim_economy.service;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;

/** How many of a team's claimed chunks are covered by the free allowance before billing kicks in. */
public final class ComplimentaryChunkAllotment {
    private ComplimentaryChunkAllotment() {
    }

    /** Config read is wrapped defensively - this runs on hot claim/unclaim paths and must never throw. */
    public static int allowance() {
        try {
            return LcClaimEconomyConfig.SERVER.freeChunks.get();
        } catch (Throwable configFailure) {
            LcClaimEconomy.LOGGER.debug("freeChunks config unavailable, defaulting to 0", configFailure);
            return 0;
        }
    }

    public static int billableChunkCount(int heldChunkCount) {
        return Math.max(0, heldChunkCount - allowance());
    }

    public static boolean isClaimFree(int heldChunkCount) {
        return heldChunkCount < allowance();
    }

    public static boolean shouldRefundOnUnclaim(int heldChunkCountBeforeUnclaim) {
        return heldChunkCountBeforeUnclaim > allowance();
    }

    /** Of {@code batchSize} chunks being claimed at once, how many fall outside whatever's left of the free allowance. */
    public static int countPaidClaimsInBatch(int heldChunkCount, int batchSize) {
        if (batchSize <= 0) {
            return 0;
        }
        int remainingFreeSlots = Math.max(0, allowance() - heldChunkCount);
        return Math.max(0, batchSize - remainingFreeSlots);
    }
}
