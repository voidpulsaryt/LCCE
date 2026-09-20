package dev.voidpulsar.lc_claim_economy.service;

import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;

/**
 * War upkeep uses a linear step model: for base upkeep {@code b}, {@code k} incoming
 * war declarers, and step {@code s}, the incoming surcharge is {@code b * k * s} —
 * every incoming war adds the same flat {@code s} fraction of base upkeep, so the
 * bill grows proportionally with the number of attackers instead of compounding.
 * Outgoing wars use a flat {@code targetBase * warOutgoingCostMultiplier}, unrelated
 * to how many wars are already declared. Total upkeep is {@code base + incoming + outgoing}.
 */
public final class ConflictBillingMath {
    private ConflictBillingMath() {
    }

    public static double warExponent() {
        return LcClaimEconomyConfig.SERVER.warCostMultiplier.get();
    }

    /** {@code (k + 1) * s} — the total linear factor across {@code k + 1} flat terms. */
    public static double geometricFactor(int k, double s) {
        if (k < 0) {
            return 1.0D;
        }
        return (k + 1) * s;
    }

    /** Incoming war copper for {@code k} declarers: {@code b * k * s}. */
    public static long incomingWarSurchargeCopper(long baseCopper, int incomingWarCount) {
        return sumOrdinalIncomingTerms(baseCopper, incomingWarCount);
    }

    /** Flat copper per incoming war: {@code base * s}, the same for every declarer regardless of ordinal position. */
    public static long ordinalWarTermCopper(long baseCopper, int n, double s) {
        if (baseCopper <= 0L || n < 0) {
            return 0L;
        }
        double result = baseCopper * s;
        if (!Double.isFinite(result) || result >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) Math.floor(result);
    }

    public static double warOutgoingMultiplier() {
        return LcClaimEconomyConfig.SERVER.warOutgoingCostMultiplier.get();
    }

    /**
     * Flat outgoing war cost: {@code targetBase * warOutgoingCostMultiplier}.
     * The same cost applies regardless of how many wars are already declared.
     */
    public static long outgoingWarCostCopper(long targetBaseCopper) {
        if (targetBaseCopper <= 0L) {
            return 0L;
        }
        double result = targetBaseCopper * warOutgoingMultiplier();
        if (!Double.isFinite(result) || result >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) Math.floor(result);
    }

    /** @deprecated Use {@link #outgoingWarCostCopper(long)} — outgoing cost is flat. */
    @Deprecated
    public static long outgoingWarTermCopper(long targetBaseCopper, int n) {
        return outgoingWarCostCopper(targetBaseCopper);
    }

    /** Total upkeep multiplier for {@code k} incoming wars: {@code 1 + k * s}. */
    public static double totalUpkeepFactor(int incomingWarCount, double s) {
        if (incomingWarCount <= 0) {
            return 1.0D;
        }
        return 1.0D + geometricFactor(incomingWarCount - 1, s);
    }

    public static String formatGeometricFactor(int k) {
        return formatGeometricFactor(k, warExponent());
    }

    public static String formatGeometricFactor(int k, double s) {
        if (k <= 0) {
            return "1";
        }
        return trimDouble(geometricFactor(k, s));
    }

    public static String formatTotalUpkeepFactor(int incomingWarCount) {
        return formatTotalUpkeepFactor(incomingWarCount, warExponent());
    }

    public static String formatTotalUpkeepFactor(int incomingWarCount, double s) {
        if (incomingWarCount <= 0) {
            return "1";
        }
        return trimDouble(totalUpkeepFactor(incomingWarCount, s));
    }

    public static String formatExtraFactor(int k) {
        return formatExtraFactor(k, warExponent());
    }

    /** The incoming multiplier beyond the base 1.0x, i.e. {@code k * s}. */
    public static String formatExtraFactor(int k, double s) {
        if (k <= 0) {
            return "0";
        }
        return trimDouble(geometricFactor(k - 1, s));
    }

    /** Human-readable incoming term sum: {@code s} repeated {@code k} times. */
    public static String formatExtraTermSum(int k) {
        return formatExtraTermSum(k, warExponent());
    }

    public static String formatExtraTermSum(int k, double s) {
        if (k <= 0) {
            return "0";
        }
        StringBuilder terms = new StringBuilder();
        String term = trimDouble(s);
        for (int n = 0; n < k; n++) {
            if (n > 0) {
                terms.append(" + ");
            }
            terms.append(term);
        }
        return terms.toString();
    }

    public static long sumOrdinalIncomingTerms(long baseCopper, int incomingWarCount) {
        return sumOrdinalIncomingTerms(baseCopper, incomingWarCount, warExponent());
    }

    static long sumOrdinalIncomingTerms(long baseCopper, int incomingWarCount, double s) {
        if (incomingWarCount <= 0 || baseCopper <= 0L) {
            return 0L;
        }
        long sum = 0L;
        for (int n = 0; n < incomingWarCount; n++) {
            long term = ordinalWarTermCopper(baseCopper, n, s);
            if (term >= Long.MAX_VALUE - sum) {
                return Long.MAX_VALUE;
            }
            sum += term;
        }
        return sum;
    }

    private static String trimDouble(double value) {
        if (Math.rint(value) == value) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
