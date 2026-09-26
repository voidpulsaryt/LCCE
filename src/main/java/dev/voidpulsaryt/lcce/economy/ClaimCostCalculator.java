package dev.voidpulsaryt.lcce.economy;

import dev.voidpulsaryt.lcce.config.LCCEConfig;

import java.math.BigInteger;

/**
 * Pure pricing math for the LCCE. Kept separate from the FTB Chunks event hooks so the
 * curve can be unit-tested/tuned without touching the integration glue.
 */
public final class ClaimCostCalculator {

    private ClaimCostCalculator() {}

    /**
     * Cost to claim the (ownedBeforeThisClaim + 1)-th chunk.
     *
     * @param ownedBeforeThisClaim how many chunks the team/player already owns, before this claim
     */
    public static BigInteger costOf(int ownedBeforeThisClaim) {
        int freeChunks = LCCEConfig.FREE_CLAIM_CHUNKS.get();
        if (ownedBeforeThisClaim < freeChunks) {
            return BigInteger.ZERO;
        }

        long baseCost = LCCEConfig.CLAIM_BASE_COST.get();
        double growth = LCCEConfig.CLAIM_COST_GROWTH.get();
        int chunksPastFree = ownedBeforeThisClaim - freeChunks;

        double cost = baseCost * Math.pow(growth, chunksPastFree);
        return BigInteger.valueOf(Math.round(cost));
    }

    /**
     * Refund granted for unclaiming a chunk, given the team/player owns {@code ownedBeforeThisUnclaim}
     * chunks (including the one about to be removed). Refund is based on the marginal cost of that
     * last chunk, i.e. the price it would currently cost to claim chunk number
     * {@code ownedBeforeThisUnclaim - 1}.
     */
    public static BigInteger refundOf(int ownedBeforeThisUnclaim) {
        if (ownedBeforeThisUnclaim <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger marginalCost = costOf(ownedBeforeThisUnclaim - 1);
        double refundPercent = LCCEConfig.UNCLAIM_REFUND_PERCENT.get();
        double refund = marginalCost.doubleValue() * refundPercent;
        return BigInteger.valueOf(Math.round(refund));
    }
}
