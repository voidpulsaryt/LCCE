package dev.voidpulsar.lc_claim_economy.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the land-chunk billing unit formula, extracted so it can run
 * without the NeoForge static class-initialiser chain that SafeguardPricing
 * itself pulls in via FTBChunksProperties.
 */
class SafeguardPricingTest {

    /** Formula mirroring SafeguardPricing.landChunkUnits(int, int) */
    private static int landChunkUnits(int landBillable, int groupSize) {
        if (landBillable <= 0) return 0;
        groupSize = Math.max(1, groupSize);
        return (landBillable + groupSize - 1) / groupSize;
    }

    @Test
    void landChunkUnits_zeroChunks_returnsZero() {
        assertEquals(0, landChunkUnits(0, 5));
    }

    @Test
    void landChunkUnits_negativeChunks_returnsZero() {
        assertEquals(0, landChunkUnits(-1, 5));
    }

    @ParameterizedTest(name = "{0} chunks / group {1} => {2} units")
    @CsvSource({
        "1,  5, 1",
        "4,  5, 1",
        "5,  5, 1",
        "6,  5, 2",
        "10, 5, 2",
        "11, 5, 3",
        "25, 5, 5",
        "1,  1, 1",
        "5,  1, 5",
        "1, 10, 1",
        "10,10, 1",
        "11,10, 2",
    })
    void landChunkUnits_parametrized(int landBillable, int groupSize, int expected) {
        assertEquals(expected, landChunkUnits(landBillable, groupSize));
    }

    @Test
    void landChunkUnits_groupSizeZeroTreatedAsOne() {
        assertDoesNotThrow(() -> landChunkUnits(5, 0));
        assertEquals(5, landChunkUnits(5, 0));
    }

    @Test
    void landChunkUnits_groupSizeOne_equalsChunkCount() {
        for (int n = 1; n <= 20; n++) {
            assertEquals(n, landChunkUnits(n, 1),
                    "Group size 1 must equal chunk count for n=" + n);
        }
    }

    @Test
    void landChunkUnits_alwaysRoundUp() {
        for (int n = 1; n <= 50; n++) {
            int units = landChunkUnits(n, 5);
            assertTrue(units * 5 >= n,
                    "units*groupSize must cover all chunks: n=" + n + " units=" + units);
        }
    }
}
