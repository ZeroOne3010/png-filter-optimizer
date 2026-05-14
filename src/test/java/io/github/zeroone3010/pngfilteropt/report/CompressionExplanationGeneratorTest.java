package io.github.zeroone3010.pngfilteropt.report;

import io.github.zeroone3010.pngfilteropt.diagnostics.DirectionalSmoothness;
import io.github.zeroone3010.pngfilteropt.diagnostics.FilterUsage;
import io.github.zeroone3010.pngfilteropt.diagnostics.FilteredStreamDiagnostics;
import io.github.zeroone3010.pngfilteropt.diagnostics.RepetitionMetrics;
import io.github.zeroone3010.pngfilteropt.diagnostics.ResidualDiagnostics;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CompressionExplanationGeneratorTest {
    private final CompressionExplanationGenerator generator = new CompressionExplanationGenerator();

    @Test
    void explainsFixedNoneScreenshotWins() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-none", diag(120, 180, 140, 320, 1.05));
        d.put("fixed-paeth", diag(70, 160, 55, 210, 1.00));
        String text = generator.explain("fixed-none", d);
        assertTrue(text.contains("fixed-none preserves literal row structure"));
        assertTrue(text.contains("fixed-none"));
    }

    @Test
    void explainsFixedUpPainterlyWins() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-up", diag(90, 260, 80, 330, 0.70));
        d.put("fixed-paeth", diag(60, 220, 45, 250, 0.70));
        String text = generator.explain("fixed-up", d);
        assertTrue(text.contains("fixed-up produces"));
        assertTrue(text.contains("Vertical smoothness is stronger"));
    }

    @Test
    void explainsFixedSubHorizontalGradients() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-sub", diag(95, 210, 35, 320, 1.35));
        d.put("fixed-paeth", diag(70, 190, 20, 300, 1.35));
        String text = generator.explain("fixed-sub", d);
        assertTrue(text.contains("fixed-sub"));
        assertTrue(text.contains("Horizontal smoothness is stronger"));
    }

    @Test
    void explainsPaethTrueWin() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-paeth", diag(88, 240, 42, 180, 1.0));
        d.put("fixed-sub", diag(72, 200, 34, 260, 1.3));
        String text = generator.explain("fixed-paeth", d);
        assertTrue(text.contains("PAETH minimizes local residual magnitude"));
        assertTrue(text.contains("PAETH wins because predictor precision"));
    }

    @Test
    void explainsStrongLocalGlobalConflict() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-up", diag(150, 300, 75, 520, 0.95));
        d.put("fixed-paeth", diag(60, 200, 28, 180, 0.95));
        String text = generator.explain("fixed-up", d);
        assertTrue(text.contains("PAETH minimizes local residual magnitude"));
        assertTrue(text.contains("fixed-up outperforms PAETH overall"));
        assertTrue(text.contains("150 vs 60"));
        assertTrue(text.contains("dramatically more"));
    }

    private static FilteredStreamDiagnostics diag(int repeated32, int longestMatch, int rowsEqualPrev, long paethResidual, double ratio) {
        return new FilteredStreamDiagnostics(16, 16, 6, 8, 4, 64, 1000, 7.0, 30, 3.0, 190, 5, 0, rowsEqualPrev, 0, usage(),
                new RepetitionMetrics(0, repeated32, 0, longestMatch, 0),
                new DirectionalSmoothness(10.0, 10.0 * ratio, ratio),
                new ResidualDiagnostics(450, 420, 410, 430, paethResidual));
    }

    private static FilterUsage usage() {
        EnumMap<PngFilter, Integer> m = new EnumMap<>(PngFilter.class);
        for (PngFilter f : PngFilter.values()) m.put(f, 0);
        return new FilterUsage(m, 0);
    }
}
