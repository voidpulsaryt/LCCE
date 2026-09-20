package dev.voidpulsar.lc_claim_economy.service;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;

public final class ComplimentaryChunkAllotment {
    private ComplimentaryChunkAllotment() {
    }

    public static int allowance() {
        try {
            return LcClaimEconomyConfig.SERVER.freeChunks.get();
        } catch (Throwable error) {
            LcClaimEconomy.LOGGER.debug("freeChunks config unavailable, defaulting to 0", error);
            return 0;
        }
    }

    public static int billableChunkCount(int claimedChunks) {
        return Math.max(0, claimedChunks - allowance());
    }

    public static boolean isClaimFree(int currentClaimedChunks) {
        return currentClaimedChunks < allowance();
    }

    public static boolean shouldRefundOnUnclaim(int claimedChunksBeforeUnclaim) {
        return claimedChunksBeforeUnclaim > allowance();
    }

    public static int countPaidClaimsInBatch(int currentClaimedChunks, int newClaims) {
        if (newClaims <= 0) {
            return 0;
        }
        int freeRemaining = Math.max(0, allowance() - currentClaimedChunks);
        return Math.max(0, newClaims - freeRemaining);
    }
}
