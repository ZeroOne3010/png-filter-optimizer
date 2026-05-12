package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.FilteredRow;
import io.github.zeroone3010.pngfilteropt.png.RawImage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Chooses a per-row filter by estimating Shannon entropy from the row byte histogram.
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
     * Histogram-based Shannon entropy estimate (bits) for one row.
     * This computes: sum(count * -log2(count/length)) over all byte values.
     */
    static double estimateEntropyBits(byte[] row) {
        if (row.length == 0) {
            return 0.0d;
        }

        int[] histogram = new int[256];
        for (byte b : row) {
            histogram[Byte.toUnsignedInt(b)]++;
        }

        double length = row.length;
        double bits = 0.0d;
        for (int count : histogram) {
            if (count == 0) {
                continue;
            }
            double probability = count / length;
            bits += count * (-log2(probability));
        }
        return bits;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0d);
    }
}
