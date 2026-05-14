package io.github.zeroone3010.pngfilteropt.report;

import io.github.zeroone3010.pngfilteropt.diagnostics.DirectionalSmoothness;
import io.github.zeroone3010.pngfilteropt.diagnostics.FilterUsage;
import io.github.zeroone3010.pngfilteropt.diagnostics.FilteredStreamDiagnostics;
import io.github.zeroone3010.pngfilteropt.diagnostics.LzParseDiagnostics;
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
        d.put("fixed-none", diag(120, 9.0, 0.75, 880, 180, 140, 320, 1.05));
        d.put("fixed-paeth", diag(70, 8.0, 0.70, 910, 160, 55, 210, 1.00));
        String text = generator.explain("fixed-none", d);
        assertTrue(text.contains("fixed-none preserves literal row structure"));
    }

    @Test
    void explainsStrongLocalGlobalConflict() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-up", diag(150, 10.0, 0.80, 700, 300, 75, 520, 0.95));
        d.put("fixed-paeth", diag(60, 7.0, 0.55, 820, 200, 28, 180, 0.95));
        String text = generator.explain("fixed-up", d);
        assertTrue(text.contains("PAETH minimizes local residual magnitude"));
        assertTrue(text.contains("stronger global repetition dominates"));
    }

    @Test
    void explainsSimilarRepetitionUsingMatchQuality() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-sub", diag(101, 11.5, 0.85, 640, 230, 20, 320, 1.35));
        d.put("fixed-paeth", diag(100, 8.0, 0.50, 760, 220, 20, 260, 1.35));
        String text = generator.explain("fixed-sub", d);
        assertTrue(text.contains("Repetition metrics are broadly similar"));
        assertTrue(text.contains("lower estimated LZ token cost"));
        assertTrue(text.contains("more short-distance matches"));
        assertTrue(text.contains("simpler and more stationary residual stream"));
    }

    @Test
    void explainsHorizontalDirectionalCoherence() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-sub", diag(95, 11.0, 0.75, 650, 210, 35, 320, 1.35));
        d.put("fixed-paeth", diag(92, 8.0, 0.60, 740, 190, 20, 300, 1.35));
        String text = generator.explain("fixed-sub", d);
        assertTrue(text.contains("strongly horizontally coherent"));
    }

    @Test
    void explainsVerticalDirectionalCoherence() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-up", diag(96, 10.5, 0.82, 660, 220, 30, 320, 0.70));
        d.put("fixed-paeth", diag(94, 7.2, 0.50, 745, 210, 20, 300, 0.70));
        String text = generator.explain("fixed-up", d);
        assertTrue(text.contains("strongly vertically coherent"));
    }

    private static FilteredStreamDiagnostics diag(int repeated32, double avgMatchLen, double shortDistanceShare,
                                                  long lzCost, int longestMatch, int rowsEqualPrev, long paethResidual, double ratio) {
        long[] distBuckets = new long[]{Math.round(shortDistanceShare * 100), 100 - Math.round(shortDistanceShare * 100), 0, 0, 0};
        LzParseDiagnostics lz = new LzParseDiagnostics(0, 100, 1000, 0, 100.0, avgMatchLen, longestMatch,
                new long[6], distBuckets, lzCost, 0, 0, 0);
        return new FilteredStreamDiagnostics(16, 16, 6, 8, 4, 64, 1000, 7.0, 30, 3.0, 190, 5, 0, rowsEqualPrev, 0, usage(),
                new RepetitionMetrics(0, repeated32, 0, longestMatch, 0),
                lz,
                new DirectionalSmoothness(10.0, 10.0 * ratio, ratio),
                new ResidualDiagnostics(450, 420, 410, 430, paethResidual));
    }

    private static FilterUsage usage() {
        EnumMap<PngFilter, Integer> m = new EnumMap<>(PngFilter.class);
        for (PngFilter f : PngFilter.values()) m.put(f, 0);
        return new FilterUsage(m, 0);
    }
}
