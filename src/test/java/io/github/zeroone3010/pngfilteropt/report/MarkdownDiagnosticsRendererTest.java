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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownDiagnosticsRendererTest {
    @Test
    void formatsDiagnosticCountsWithFigureSpaceThousandsSeparators() {
        assertEquals("1\u2007234\u2007567", MarkdownDiagnosticsRenderer.formatNumber(1_234_567));
        assertEquals("-9\u2007876", MarkdownDiagnosticsRenderer.formatNumber(-9_876));
    }

    @Test
    void rendersOutcomeSummaryBeforeLikelyExplanation() {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("best", "entropy");
        image.put("strategies", new LinkedHashMap<>(Map.of(
                "entropy", 162_677L,
                "adaptive", 166_354L
        )));
        image.put("diagnostics", new LinkedHashMap<>(Map.of(
                "entropy", diagnostics(),
                "adaptive", diagnostics()
        )));

        StringBuilder rendered = new StringBuilder();
        new MarkdownDiagnosticsRenderer().appendDiagnostics(rendered, image, false);

        assertTrue(rendered.toString().startsWith(
                "Winning strategy: `entropy` beat `adaptive` by a small margin of 3\u2007677 bytes (2.21% smaller).\n\n" +
                        "Likely explanation:\n"));
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

    @Test
    void diagnosticsExposeEveryMetricCitedByExplanations() {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("best", "fixed-paeth");
        image.put("strategies", Map.of("fixed-paeth", 10L));
        image.put("diagnostics", Map.of("fixed-paeth", diagnostics()));
        StringBuilder rendered = new StringBuilder();
        new MarkdownDiagnosticsRenderer().appendDiagnostics(rendered, image, false);
        assertTrue(rendered.toString().contains("Repeated 32B"));
        assertTrue(rendered.toString().contains("Repeated rows"));
        assertTrue(rendered.toString().contains("LZ cost bits"));
        assertTrue(rendered.toString().contains("Avg match len"));
        assertTrue(rendered.toString().contains("Short dist %"));
    }

    private static FilteredStreamDiagnostics diagnostics() {
        EnumMap<PngFilter, Integer> counts = new EnumMap<>(PngFilter.class);
        for (PngFilter filter : PngFilter.values()) counts.put(filter, 0);
        counts.put(PngFilter.PAETH, 1);

        return new FilteredStreamDiagnostics(1, 1, 6, 8, 4, 4, 5, 7.0, 1, 20.0, 5, 1, 0, 0, 0,
                new FilterUsage(counts, 1),
                new RepetitionMetrics(0, 0, 0, 0, 0),
                new LzParseDiagnostics(1, 0, 0, 5, 0.0, 0.0, 0, new long[6], new long[5], 40, 0.0, 0.0, 0.0),
                new DirectionalSmoothness(1.0, 1.0, 1.0),
                new ResidualDiagnostics(10, 8, 7, 9, 6));
    }
}
