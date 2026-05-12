package io.github.zeroone3010.pngfilteropt.optimize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EntropyEstimatorTest {
    @Test
    void distinguishesRowsWithSameValueHistogramButDifferentOrdering() {
        byte[] longRuns = new byte[]{1, 1, 1, 1, 2, 2, 2, 2};
        byte[] alternating = new byte[]{1, 2, 1, 2, 1, 2, 1, 2};

        double longRunScore = EntropyOptimizer.estimateEntropyBits(longRuns);
        double alternatingScore = EntropyOptimizer.estimateEntropyBits(alternating);

        assertTrue(alternatingScore < longRunScore,
                "Rows with different byte ordering should produce different scores even when value histograms match.");
    }
}
