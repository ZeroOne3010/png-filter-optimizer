package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.FilteredRow;
import io.github.zeroone3010.pngfilteropt.png.RawImage;

import java.util.ArrayList;
import java.util.List;

/**
 * Chooses a per-row filter by estimating Shannon coding cost.
 *
 * <p>Each candidate row is scored from row-local histograms:
 * <ul>
 *   <li>0th-order byte entropy (value histogram), and</li>
 *   <li>1st-order transition entropy (bigram histogram) to include byte-order effects.</li>
 * </ul>
 * Lower estimated entropy generally implies lower DEFLATE coding cost.
 */
public final class EntropyOptimizer implements FilterOptimizer {
    @Override
    public String name() { return "entropy"; }

    @Override
    public FilteredImage optimize(RawImage image, CandidateGenerator candidates) {
        List<FilteredRow> rows = new ArrayList<>(image.height());
        for (int y = 0; y < image.height(); y++) {
            var all = candidates.generateCandidates(image, y);
            rows.add(all.stream().min(this::compareByEstimatedCost).orElse(new FilteredRow(y, PngFilter.NONE, image.rows().get(y))));
        }
        return new FilteredImage(image, rows);
    }

    private int compareByEstimatedCost(FilteredRow left, FilteredRow right) {
        int byEntropy = Double.compare(estimateEntropyBits(left.filteredBytes()), estimateEntropyBits(right.filteredBytes()));
        if (byEntropy != 0) {
            return byEntropy;
        }

        // Deterministic tie-break: prefer the numerically smallest PNG filter id.
        return Integer.compare(left.filter().pngValue(), right.filter().pngValue());
    }

    /**
     * Shannon-entropy estimate (bits) for one row.
     *
     * <p>Scoring is histogram-based and row-local:
     * value histogram cost + transition histogram cost.
     * Rows with equal byte counts but different order can therefore score differently.
     */
    static double estimateEntropyBits(byte[] row) {
        if (row.length == 0) {
            return 0.0d;
        }

        double valueBits = estimateValueEntropyBits(row);
        double transitionBits = estimateTransitionEntropyBits(row);
        return valueBits + transitionBits;
    }

    private static double estimateValueEntropyBits(byte[] row) {
        int[] histogram = new int[256];
        for (byte b : row) {
            histogram[Byte.toUnsignedInt(b)]++;
        }

        return entropyBitsFromHistogram(histogram, row.length);
    }

    private static double estimateTransitionEntropyBits(byte[] row) {
        if (row.length < 2) {
            return 0.0d;
        }

        int[] bigramHistogram = new int[256 * 256];
        for (int i = 1; i < row.length; i++) {
            int prev = Byte.toUnsignedInt(row[i - 1]);
            int curr = Byte.toUnsignedInt(row[i]);
            bigramHistogram[(prev << 8) | curr]++;
        }

        return entropyBitsFromHistogram(bigramHistogram, row.length - 1);
    }

    private static double entropyBitsFromHistogram(int[] histogram, int sampleCount) {
        double total = sampleCount;
        double bits = 0.0d;
        for (int count : histogram) {
            if (count == 0) {
                continue;
            }
            double probability = count / total;
            bits += count * (-log2(probability));
        }
        return bits;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0d);
    }
}
