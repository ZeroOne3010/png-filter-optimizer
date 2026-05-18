package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.png.RawImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HierarchicalSplitOptimizerTest {
    private final CandidateGenerator candidates = new CandidateGenerator();

    @Test
    void deterministicAcrossRuns() {
        RawImage image = TestData.raw(16, 16);
        var optimizer = new HierarchicalSplitOptimizer(5, 1);
        var first = optimizer.optimize(image, candidates);
        var second = optimizer.optimize(image, candidates);
        assertEquals(first.rows(), second.rows());
    }

    @Test
    void zeroDepthStaysOnGlobalFixedSeed() {
        RawImage image = TestData.raw(8, 8);
        var optimizer = new HierarchicalSplitOptimizer(0, 1);
        var result = optimizer.optimize(image, candidates);
        PngFilter seed = result.rows().get(0).filter();
        assertTrue(result.rows().stream().allMatch(r -> r.filter() == seed));
    }

    @Test
    void minSegmentRowsCanPreventSplitRefinement() {
        RawImage image = TestData.raw(6, 6);
        var noSplit = new HierarchicalSplitOptimizer(6, image.height() + 1).optimize(image, candidates);
        PngFilter seed = noSplit.rows().get(0).filter();
        assertTrue(noSplit.rows().stream().allMatch(r -> r.filter() == seed));
    }

    static class TestData {
        static RawImage raw(int w, int h) {
            java.util.List<byte[]> rows = new java.util.ArrayList<>();
            for (int y = 0; y < h; y++) {
                byte[] row = new byte[w * 4];
                for (int x = 0; x < row.length; x++) row[x] = (byte) ((x * 13 + y * 7) & 0xff);
                rows.add(row);
            }
            return new RawImage(w, h, 8, 6, 4, w * 4, rows);
        }
    }
}
