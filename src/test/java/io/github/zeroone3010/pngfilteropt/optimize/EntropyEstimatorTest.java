package io.github.zeroone3010.pngfilteropt.optimize;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EntropyEstimatorTest {
    @Test
    void prefersLowEntropyRowsOverHighEntropyRows() {
        byte[] lowEntropy = new byte[]{7, 7, 7, 7, 7, 7, 7, 7};
        byte[] highEntropy = new byte[]{0, 1, 2, 3, 4, 5, 6, 7};

        double lowEntropyScore = EntropyOptimizer.estimateEntropyBits(lowEntropy);
        double highEntropyScore = EntropyOptimizer.estimateEntropyBits(highEntropy);

        assertTrue(lowEntropyScore < highEntropyScore,
                "Low-entropy synthetic rows should receive a smaller estimated cost.");
    }

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
