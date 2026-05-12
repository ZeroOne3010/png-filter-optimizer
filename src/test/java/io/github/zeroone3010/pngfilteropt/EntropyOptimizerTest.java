package io.github.zeroone3010.pngfilteropt;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.optimize.EntropyOptimizer;
import io.github.zeroone3010.pngfilteropt.png.RawImage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntropyOptimizerTest {
    private final EntropyOptimizer optimizer = new EntropyOptimizer();
    private final CandidateGenerator candidates = new CandidateGenerator();

    @Test
    void prefersLowEntropyRows() {
        RawImage image = rawImage(8, 1, List.of(new byte[]{50, 50, 50, 50, 50, 50, 50, 50}));

        var out = optimizer.optimize(image, candidates);

        assertEquals(PngFilter.SUB, out.rows().get(0).filter());
    }

    @Test
    void prefersLowerEstimatedEntropyOnHighVarianceRows() {
        RawImage image = rawImage(8, 1, List.of(new byte[]{0, 127, 1, 126, 2, 125, 3, 124}));

        var out = optimizer.optimize(image, candidates);

        assertEquals(PngFilter.PAETH, out.rows().get(0).filter());
    }

    @Test
    void tieUsesDeterministicFilterOrder() {
        RawImage image = rawImage(2, 1, List.of(new byte[]{0, 0}));

        var out = optimizer.optimize(image, candidates);

        assertEquals(PngFilter.NONE, out.rows().get(0).filter());
    }

    private static RawImage rawImage(int width, int height, List<byte[]> rows) {
        return new RawImage(width, height, 8, 0, 1, width, rows);
    }
}
