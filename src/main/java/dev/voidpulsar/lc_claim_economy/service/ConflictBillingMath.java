package dev.voidpulsar.lc_claim_economy.service;

import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;

import java.util.Collections;
import java.util.Locale;

/**
 * Pure math for turning war counts into copper, kept free of any server/team lookups so it
 * can be unit tested in isolation.
 *
 * <p>Incoming wars are billed as flat steps rather than a compounding series: with base
 * upkeep {@code b}, {@code k} teams currently at war with you, and step size {@code s}, each
 * attacker adds the same {@code b * s} surcharge, so {@code k} attackers together add
 * {@code b * k * s}. Declaring an outgoing war is a flat {@code targetBase *
 * warOutgoingCostMultiplier} regardless of how many wars you're already in. A team's final
 * bill is {@code base + incomingSurcharge + outgoingCost}.
 */
public final class ConflictBillingMath {

    private ConflictBillingMath() {
    }

    // ------------------------------------------------------------------
    // Config-backed multipliers
    // ------------------------------------------------------------------

    public static double warExponent() {
        return LcClaimEconomyConfig.SERVER.warCostMultiplier.get();
    }

    public static double warOutgoingMultiplier() {
        return LcClaimEconomyConfig.SERVER.warOutgoingCostMultiplier.get();
    }

    // ------------------------------------------------------------------
    // Raw copper math
    // ------------------------------------------------------------------

    /** Total linear factor across {@code k + 1} flat {@code s}-sized steps: {@code (k + 1) * s}. */
    public static double geometricFactor(int stepCount, double stepSize) {
        if (stepCount < 0) {
            return 1.0D;
        }
        return (stepCount + 1) * stepSize;
    }

    /** Sums the flat per-war surcharge across every incoming attacker. */
    public static long incomingWarSurchargeCopper(long baseCopper, int incomingWarCount) {
        return sumOrdinalIncomingTerms(baseCopper, incomingWarCount);
    }

    /**
     * Copper contributed by a single incoming war: {@code base * s}. Despite taking an
     * ordinal index {@code n}, the result is identical for every attacker — the parameter is
     * kept so callers can still reason about "the Nth war's cost" symmetrically with the sum.
     */
    public static long ordinalWarTermCopper(long baseCopper, int ordinalIndex, double stepSize) {
        if (baseCopper <= 0L || ordinalIndex < 0) {
            return 0L;
        }
        double rawCopper = baseCopper * stepSize;
        if (!Double.isFinite(rawCopper) || rawCopper >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) Math.floor(rawCopper);
    }

    /**
     * Flat cost of declaring a new outgoing war: {@code targetBase * warOutgoingCostMultiplier}.
     * Unlike incoming surcharges, this never depends on how many wars are already active.
     */
    public static long outgoingWarCostCopper(long targetBaseCopper) {
        if (targetBaseCopper <= 0L) {
            return 0L;
        }
        double rawCopper = targetBaseCopper * warOutgoingMultiplier();
        if (!Double.isFinite(rawCopper) || rawCopper >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) Math.floor(rawCopper);
    }

    /** @deprecated outgoing cost no longer depends on war ordinal — use {@link #outgoingWarCostCopper(long)}. */
    @Deprecated
    public static long outgoingWarTermCopper(long targetBaseCopper, int ordinalIndex) {
        return outgoingWarCostCopper(targetBaseCopper);
    }

    /** Multiplier applied to base upkeep once {@code incomingWarCount} attackers are counted: {@code 1 + k * s}. */
    public static double totalUpkeepFactor(int incomingWarCount, double stepSize) {
        if (incomingWarCount <= 0) {
            return 1.0D;
        }
        return 1.0D + geometricFactor(incomingWarCount - 1, stepSize);
    }

    public static long sumOrdinalIncomingTerms(long baseCopper, int incomingWarCount) {
        return sumOrdinalIncomingTerms(baseCopper, incomingWarCount, warExponent());
    }

    /** Adds up {@link #ordinalWarTermCopper} for every attacker, capping at {@code Long.MAX_VALUE}. */
    static long sumOrdinalIncomingTerms(long baseCopper, int incomingWarCount, double stepSize) {
        if (incomingWarCount <= 0 || baseCopper <= 0L) {
            return 0L;
        }
        long runningTotal = 0L;
        for (int attackerIndex = 0; attackerIndex < incomingWarCount; attackerIndex++) {
            long perAttackerCopper = ordinalWarTermCopper(baseCopper, attackerIndex, stepSize);
            if (perAttackerCopper >= Long.MAX_VALUE - runningTotal) {
                return Long.MAX_VALUE;
            }
            runningTotal += perAttackerCopper;
        }
        return runningTotal;
    }

    // ------------------------------------------------------------------
    // Human-readable renderings of the same math, for tooltips/messages
    // ------------------------------------------------------------------

    public static String formatGeometricFactor(int stepCount) {
        return formatGeometricFactor(stepCount, warExponent());
    }

    public static String formatGeometricFactor(int stepCount, double stepSize) {
        if (stepCount <= 0) {
            return "1";
        }
        return trimDouble(geometricFactor(stepCount, stepSize));
    }

    public static String formatTotalUpkeepFactor(int incomingWarCount) {
        return formatTotalUpkeepFactor(incomingWarCount, warExponent());
    }

    public static String formatTotalUpkeepFactor(int incomingWarCount, double stepSize) {
        if (incomingWarCount <= 0) {
            return "1";
        }
        return trimDouble(totalUpkeepFactor(incomingWarCount, stepSize));
    }

    public static String formatExtraFactor(int incomingWarCount) {
        return formatExtraFactor(incomingWarCount, warExponent());
    }

    /** The multiplier beyond the base 1.0x that incoming wars add on top: {@code k * s}. */
    public static String formatExtraFactor(int incomingWarCount, double stepSize) {
        if (incomingWarCount <= 0) {
            return "0";
        }
        return trimDouble(geometricFactor(incomingWarCount - 1, stepSize));
    }

    public static String formatExtraTermSum(int incomingWarCount) {
        return formatExtraTermSum(incomingWarCount, warExponent());
    }

    /** Spells out the incoming surcharge as {@code s + s + ... + s}, one term per attacker. */
    public static String formatExtraTermSum(int incomingWarCount, double stepSize) {
        if (incomingWarCount <= 0) {
            return "0";
        }
        String singleTerm = trimDouble(stepSize);
        return String.join(" + ", Collections.nCopies(incomingWarCount, singleTerm));
    }

    /** Drops the decimal point for whole numbers, otherwise renders to two decimal places. */
    private static String trimDouble(double value) {
        if (Math.rint(value) == value) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
