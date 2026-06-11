package io.github.zeroone3010.pngfilteropt.report;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownDiagnosticsRendererTest {
    @Test
    void formatsDiagnosticCountsWithFigureSpaceThousandsSeparators() {
        assertEquals("1\u2007234\u2007567", MarkdownDiagnosticsRenderer.formatNumber(1_234_567));
        assertEquals("-9\u2007876", MarkdownDiagnosticsRenderer.formatNumber(-9_876));
    }

    @Test
    void outcomeSummaryNamesWinningStrategyAndMargin() {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("best", "fixed-sub");
        image.put("strategies", new LinkedHashMap<>(Map.of(
                "fixed-sub", 980L,
                "fixed-paeth", 1000L,
                "fixed-up", 1200L
        )));

        String summary = MarkdownDiagnosticsRenderer.outcomeSummary(image).orElseThrow();

        assertTrue(summary.startsWith("Winning strategy: `fixed-sub` beat `fixed-paeth`"));
        assertTrue(summary.contains("small margin of 20 bytes (2.00% smaller)"));
    }

    @Test
    void outcomeSummarySkipsGeneticWhenItMatchesWinnerSize() {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("best", "fixed-up");
        Map<String, Long> strategies = new LinkedHashMap<>();
        strategies.put("fixed-up", 1_000L);
        strategies.put("genetic", 1_000L);
        strategies.put("fixed-paeth", 1_060L);
        strategies.put("delta-control", -15L);
        image.put("strategies", strategies);

        String summary = MarkdownDiagnosticsRenderer.outcomeSummary(image).orElseThrow();

        assertTrue(summary.startsWith("Winning strategy: `fixed-up` beat `fixed-paeth`"));
        assertTrue(summary.contains("moderate margin of 60 bytes (5.66% smaller)"));
    }
}
