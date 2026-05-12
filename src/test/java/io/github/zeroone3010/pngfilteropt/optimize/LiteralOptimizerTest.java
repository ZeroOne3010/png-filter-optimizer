package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.png.RawImage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiteralOptimizerTest {
    private final LiteralOptimizer optimizer = new LiteralOptimizer();
    private final CandidateGenerator candidates = new CandidateGenerator();

    @Test
    void alwaysChoosesNoneFilter() {
        RawImage image = rawImage(6, 3, List.of(
                new byte[]{1, 2, 3, 4, 5, 6},
                new byte[]{7, 6, 5, 4, 3, 2},
                new byte[]{9, 9, 9, 9, 9, 9}
        ));

        var out = optimizer.optimize(image, candidates);

        assertEquals(3, out.rows().size());
        out.rows().forEach(row -> assertEquals(PngFilter.NONE, row.filter()));
    }

    private static RawImage rawImage(int width, int height, List<byte[]> rows) {
        return new RawImage(width, height, 8, 0, 1, width, rows);
    }
}
