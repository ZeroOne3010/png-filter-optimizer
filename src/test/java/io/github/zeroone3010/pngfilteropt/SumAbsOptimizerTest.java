package io.github.zeroone3010.pngfilteropt;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.optimize.SumAbsOptimizer;
import io.github.zeroone3010.pngfilteropt.png.RawImage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SumAbsOptimizerTest {
    private final SumAbsOptimizer optimizer = new SumAbsOptimizer();
    private final CandidateGenerator candidates = new CandidateGenerator();

    @Test
    void selectsBestFilterForSimpleSyntheticImage() {
        RawImage image = rawImage(3, 1, List.of(new byte[]{10, 11, 12}));

        var out = optimizer.optimize(image, candidates);

        assertEquals(1, out.rows().size());
        assertEquals(PngFilter.SUB, out.rows().get(0).filter());
    }

    @Test
    void tieUsesDeterministicFilterOrder() {
        RawImage image = rawImage(2, 1, List.of(new byte[]{0, 0}));

        var out = optimizer.optimize(image, candidates);

        assertEquals(PngFilter.NONE, out.rows().get(0).filter());
    }

    @Test
    void selectsOneFilterPerRowForMultiRowImage() {
        RawImage image = rawImage(3, 2, List.of(
                new byte[]{100, 100, 100},
                new byte[]{100, 100, 100}
        ));

        var out = optimizer.optimize(image, candidates);

        assertEquals(2, out.rows().size());
        assertEquals(PngFilter.SUB, out.rows().get(0).filter());
        assertEquals(PngFilter.UP, out.rows().get(1).filter());
    }

    private static RawImage rawImage(int width, int height, List<byte[]> rows) {
        return new RawImage(width, height, 8, 0, 1, width, rows);
    }
}
