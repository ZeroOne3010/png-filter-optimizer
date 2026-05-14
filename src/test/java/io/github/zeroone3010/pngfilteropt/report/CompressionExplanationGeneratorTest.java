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
import static org.junit.jupiter.api.Assertions.assertFalse;

class CompressionExplanationGeneratorTest {
    private final CompressionExplanationGenerator generator = new CompressionExplanationGenerator();

    @Test
    void explainsFixedNoneLiteralRowPreservation() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-none", diag(5, 100, 80, 300, 1.1));
        d.put("fixed-paeth", diag(2, 40, 60, 200, 1.0));
        String text = generator.explain("fixed-none", d);
        assertTrue(text.contains("literal row repetition"));
    }

    @Test
    void explainsUpWinsFromVerticalAndRepeatedStructures() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-up", diag(40, 110, 100, 350, 0.7));
        d.put("fixed-paeth", diag(12, 80, 90, 320, 0.7));
        String text = generator.explain("fixed-up", d);
        assertTrue(text.contains("Vertical smoothness"));
        assertTrue(text.contains("DEFLATE-friendly"));
    }

    @Test
    void explainsPaethLocalButNonPaethWinnerConflict() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-up", diag(45, 140, 120, 400, 0.95));
        d.put("fixed-paeth", diag(10, 70, 100, 350, 0.95));
        String text = generator.explain("fixed-up", d);
        assertTrue(text.contains("Residual diagnostics favor PAETH locally"));
        assertTrue(text.contains("Global repetition appears to outweigh local residual minimization"));
    }

    @Test
    void explainsHorizontalGradientAsSubFriendly() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-sub", diag(25, 90, 90, 280, 1.35));
        String text = generator.explain("fixed-sub", d);
        assertTrue(text.contains("Horizontal coherence"));
    }

    @Test
    void explainsRandomNoiseAsTradeoff() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("adaptive", diag(0, 0, 0, 20, 1.0));
        d.put("fixed-none", diag(0, 0, 0, 20, 1.0));
        String text = generator.explain("adaptive", d);
        assertTrue(text.contains("tradeoff"));
        assertFalse(text.contains("strong global back-reference opportunities"));
    }

    private static FilteredStreamDiagnostics diag(int repeated32, int longestMatch, int rowsEqualPrev, long paethResidual, double ratio) {
        return new FilteredStreamDiagnostics(16, 16, 6, 8, 4, 64, 1000, 7.0, 30, 3.0, 190, 5, rowsEqualPrev, 0, 0, usage(),
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
