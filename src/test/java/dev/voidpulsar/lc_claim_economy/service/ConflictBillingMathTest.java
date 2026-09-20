package dev.voidpulsar.lc_claim_economy.service;

import org.junit.jupiter.api.Test;

import static dev.voidpulsar.lc_claim_economy.service.ConflictBillingMath.*;
import static org.junit.jupiter.api.Assertions.*;

class ConflictBillingMathTest {

    // --- geometricFactor: (k + 1) * s ---

    @Test
    void geometricFactor_negativeK_returnsOne() {
        assertEquals(1.0, geometricFactor(-1, 2.0));
    }

    @Test
    void geometricFactor_zeroWars_isSingleStep() {
        assertEquals(2.0, geometricFactor(0, 2.0));
    }

    @Test
    void geometricFactor_oneWar_s2() {
        assertEquals(4.0, geometricFactor(1, 2.0));
    }

    @Test
    void geometricFactor_twoWars_s2() {
        assertEquals(6.0, geometricFactor(2, 2.0));
    }

    @Test
    void geometricFactor_threeWars_s2() {
        assertEquals(8.0, geometricFactor(3, 2.0));
    }

    @Test
    void geometricFactor_fractionalStep() {
        assertEquals(3.0, geometricFactor(1, 1.5), 1e-9);
    }

    // --- ordinalWarTermCopper: base * s, independent of ordinal position n ---

    @Test
    void ordinalTerm_zeroBase_returnsZero() {
        assertEquals(0L, ordinalWarTermCopper(0L, 0, 2.0));
    }

    @Test
    void ordinalTerm_negativeBase_returnsZero() {
        assertEquals(0L, ordinalWarTermCopper(-100L, 0, 2.0));
    }

    @Test
    void ordinalTerm_negativeIndex_returnsZero() {
        assertEquals(0L, ordinalWarTermCopper(100L, -1, 2.0));
    }

    @Test
    void ordinalTerm_firstWar_flatStep() {
        assertEquals(200L, ordinalWarTermCopper(100L, 0, 2.0));
    }

    @Test
    void ordinalTerm_sameFlatStep_regardlessOfPosition() {
        assertEquals(200L, ordinalWarTermCopper(100L, 1, 2.0));
        assertEquals(200L, ordinalWarTermCopper(100L, 5, 2.0));
        assertEquals(200L, ordinalWarTermCopper(100L, 50, 2.0));
    }

    @Test
    void ordinalTerm_fractionalStep() {
        assertEquals(150L, ordinalWarTermCopper(100L, 1, 1.5));
    }

    @Test
    void ordinalTerm_overflow_cappedAtLongMax() {
        long result = ordinalWarTermCopper(Long.MAX_VALUE / 2, 100, 2.0);
        assertEquals(Long.MAX_VALUE, result);
    }

    @Test
    void ordinalTerm_extremeStep_cappedAtLongMax() {
        long result = ordinalWarTermCopper(Long.MAX_VALUE, 0, 1e300);
        assertEquals(Long.MAX_VALUE, result);
    }

    @Test
    void ordinalTerm_neverNegative() {
        for (int n = 0; n <= 200; n++) {
            long v = ordinalWarTermCopper(1_000_000L, n, 2.0);
            assertTrue(v >= 0, "Negative at n=" + n);
        }
    }

    // --- sumOrdinalIncomingTerms: k * (base * s) ---

    @Test
    void sumOrdinal_zeroWars_returnsZero() {
        assertEquals(0L, sumOrdinalIncomingTerms(100L, 0, 2.0));
    }

    @Test
    void sumOrdinal_zeroBase_returnsZero() {
        assertEquals(0L, sumOrdinalIncomingTerms(0L, 3, 2.0));
    }

    @Test
    void sumOrdinal_oneWar_singleFlatStep() {
        assertEquals(200L, sumOrdinalIncomingTerms(100L, 1, 2.0));
    }

    @Test
    void sumOrdinal_twoWars_s2() {
        assertEquals(400L, sumOrdinalIncomingTerms(100L, 2, 2.0));
    }

    @Test
    void sumOrdinal_threeWars_s2() {
        assertEquals(600L, sumOrdinalIncomingTerms(100L, 3, 2.0));
    }

    @Test
    void sumOrdinal_overflow_cappedAtLongMax() {
        long result = sumOrdinalIncomingTerms(Long.MAX_VALUE, 10, 2.0);
        assertEquals(Long.MAX_VALUE, result);
    }

    @Test
    void sumOrdinal_neverNegative() {
        for (int wars = 1; wars <= 100; wars++) {
            long v = sumOrdinalIncomingTerms(1_000_000_000L, wars, 2.0);
            assertTrue(v >= 0, "Negative at wars=" + wars);
        }
    }

    @Test
    void sumOrdinal_growsLinearlyWithWarCount() {
        long oneWar = sumOrdinalIncomingTerms(1_000_000L, 1, 0.35);
        long fiveWars = sumOrdinalIncomingTerms(1_000_000L, 5, 0.35);
        assertEquals(oneWar * 5, fiveWars);
    }

    // --- totalUpkeepFactor: 1 + k * s ---

    @Test
    void totalUpkeepFactor_zeroIncomingWars_isOne() {
        assertEquals(1.0, totalUpkeepFactor(0, 1.2));
    }

    @Test
    void totalUpkeepFactor_oneIncomingWar() {
        assertEquals(2.2, totalUpkeepFactor(1, 1.2), 1e-9);
    }

    @Test
    void totalUpkeepFactor_threeIncomingWars() {
        assertEquals(1.0 + 3 * 0.35, totalUpkeepFactor(3, 0.35), 1e-9);
    }

    // --- format helpers ---

    @Test
    void formatExtraTermSum_zeroWars_returnsZero() {
        assertEquals("0", formatExtraTermSum(0, 1.2));
    }

    @Test
    void formatExtraTermSum_oneWar_showsSingleStep() {
        assertEquals("1.20", formatExtraTermSum(1, 1.2));
    }

    @Test
    void formatExtraTermSum_twoWars_repeatsStep() {
        assertEquals("1.20 + 1.20", formatExtraTermSum(2, 1.2));
    }

    @Test
    void formatTotalUpkeepFactor_zeroWars_returnsOne() {
        assertEquals("1", formatTotalUpkeepFactor(0, 1.2));
    }

    @Test
    void formatTotalUpkeepFactor_oneIncomingWar() {
        assertEquals("2.20", formatTotalUpkeepFactor(1, 1.2));
    }

    @Test
    void formatGeometricFactor_zeroWars_returnsOne() {
        assertEquals("1", formatGeometricFactor(0, 2.0));
    }

    @Test
    void formatGeometricFactor_oneWar_s2() {
        assertEquals("4", formatGeometricFactor(1, 2.0));
    }

    @Test
    void formatGeometricFactor_twoWars_s1() {
        assertEquals("3", formatGeometricFactor(2, 1.0));
    }

    @Test
    void formatExtraFactor_zeroWars_returnsZero() {
        assertEquals("0", formatExtraFactor(0, 1.2));
    }

    @Test
    void formatExtraFactor_twoWars_s1_2() {
        assertEquals("2.40", formatExtraFactor(2, 1.2));
    }
}
