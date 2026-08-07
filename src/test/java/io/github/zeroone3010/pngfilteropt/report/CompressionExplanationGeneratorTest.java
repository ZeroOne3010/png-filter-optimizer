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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompressionExplanationGeneratorTest {
    private final CompressionExplanationGenerator generator = new CompressionExplanationGenerator();

    @Test
    void explainsFixedNoneScreenshotWins() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-none", diag(120, 9.0, 0.75, 880, 180, 140, 320, 1.05, usage(PngFilter.NONE, 100)));
        d.put("fixed-paeth", diag(70, 8.0, 0.70, 910, 160, 55, 210, 1.00, usage(PngFilter.PAETH, 100)));
        String text = generator.explain("fixed-none", d);
        assertTrue(text.contains("fixed-none preserves literal row structure"));
    }

    @Test
    void explainsStrongLocalGlobalConflict() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-up", diag(150, 10.0, 0.80, 700, 300, 75, 520, 0.95, usage(PngFilter.UP, 100)));
        d.put("fixed-paeth", diag(60, 7.0, 0.55, 820, 200, 28, 180, 0.95, usage(PngFilter.PAETH, 100)));
        String text = generator.explain("fixed-up", d);
        assertTrue(text.contains("PAETH minimizes local residual magnitude"));
        assertTrue(text.contains("stronger global repetition dominates"));
    }

    @Test
    void explainsSimilarRepetitionUsingMatchQuality() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-sub", diag(101, 11.5, 0.85, 640, 230, 20, 320, 1.35, usage(PngFilter.SUB, 100)));
        d.put("fixed-paeth", diag(100, 8.0, 0.50, 760, 220, 20, 260, 1.35, usage(PngFilter.PAETH, 100)));
        String text = generator.explain("fixed-sub", d);
        assertTrue(text.contains("Repetition metrics are broadly similar"));
        assertTrue(text.contains("lower estimated LZ token cost"));
        assertTrue(text.contains("more short-distance matches"));
        assertTrue(text.contains("simpler and more stationary residual stream"));
    }


    @Test
    void avoidsOverclaimingAllMatchSignalsWhenOnlyOneImproves() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-sub", diag(101, 7.0, 0.45, 640, 230, 20, 320, 1.35, usage(PngFilter.SUB, 100)));
        d.put("fixed-paeth", diag(100, 8.0, 0.50, 760, 220, 20, 260, 1.35, usage(PngFilter.PAETH, 100)));
        String text = generator.explain("fixed-sub", d);
        assertTrue(text.contains("match quality differs more than raw repetition count"));
        assertFalse(text.contains("lower estimated LZ token cost, longer average matches, and more short-distance matches"));
    }

    @Test
    void explainsHorizontalDirectionalCoherence() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-sub", diag(95, 11.0, 0.75, 650, 210, 35, 320, 1.35, usage(PngFilter.SUB, 100)));
        d.put("fixed-paeth", diag(92, 8.0, 0.60, 740, 190, 20, 300, 1.35, usage(PngFilter.PAETH, 100)));
        String text = generator.explain("fixed-sub", d);
        assertTrue(text.contains("strongly horizontally coherent"));
    }

    @Test
    void explainsVerticalDirectionalCoherence() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-up", diag(96, 10.5, 0.82, 660, 220, 30, 320, 0.70, usage(PngFilter.UP, 100)));
        d.put("fixed-paeth", diag(94, 7.2, 0.50, 745, 210, 20, 300, 0.70, usage(PngFilter.PAETH, 100)));
        String text = generator.explain("fixed-up", d);
        assertTrue(text.contains("strongly vertically coherent"));
    }

    @Test
    void rendersDistinctCompressionInsightLayer() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-sub", diag(102, 12.0, 0.82, 620, 230, 18, 320, 1.40, usage(PngFilter.SUB, 100)));
        d.put("fixed-paeth", diag(100, 8.0, 0.50, 760, 200, 18, 260, 1.40, usage(PngFilter.PAETH, 100)));
        String likely = generator.metricExplanation("fixed-sub", d);
        String insight = generator.compressionInsight("fixed-sub", d);
        assertTrue(likely.contains("LZ token cost"));
        assertTrue(insight.contains("regular DEFLATE-friendly residual language") || insight.contains("directional structure"));
        assertFalse(likely.equals(insight));
    }

    @Test
    void detectsConsistencyDominance() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-sub", diag(200, 9.0, 0.70, 700, 200, 0, 500, 1.10, usage(PngFilter.SUB, 100)));
        d.put("adaptive", diag(205, 8.8, 0.68, 720, 190, 0, 480, 1.10, mixedUsage(45, 40, 10, 5, 0)));
        String insight = generator.compressionInsight("fixed-sub", d, true);
        assertTrue(insight.contains("Globally consistent predictors") || insight.contains("consistency-dominates"));
    }

    @Test
    void detectsFixedNoneSurpriseAsLiteralStructure() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-none", diag(300, 35.0, 0.70, 500, 300, 120, 250, 1.0, usage(PngFilter.NONE, 100)));
        d.put("fixed-paeth", diag(160, 8.0, 0.50, 800, 160, 10, 100, 1.0, usage(PngFilter.PAETH, 100)));
        String insight = generator.compressionInsight("fixed-none", d);
        assertTrue(insight.contains("exact repeated row structure"));
    }

    @Test
    void detectsUsefulLocalExceptions() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("genetic", diag(260, 14.0, 0.80, 640, 240, 0, 420, 1.30, mixedUsage(82, 12, 6, 0, 0)));
        d.put("fixed-sub", diag(250, 13.0, 0.76, 690, 220, 0, 420, 1.30, usage(PngFilter.SUB, 100)));
        d.put("fixed-paeth", diag(210, 9.0, 0.55, 760, 200, 0, 360, 1.30, usage(PngFilter.PAETH, 100)));
        String insight = generator.compressionInsight("genetic", d);
        assertTrue(insight.contains("localized exceptions") || insight.contains("fixed-SUB image"));
    }

    @Test
    void detectsPaethLocallyBestButGloballyWorse() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-sub", diag(200, 11.0, 0.75, 600, 230, 0, 400, 1.2, usage(PngFilter.SUB, 100)));
        d.put("fixed-paeth", diag(180, 8.0, 0.55, 720, 180, 0, 150, 1.2, usage(PngFilter.PAETH, 100)));
        String insight = generator.compressionInsight("fixed-sub", d);
        assertTrue(insight.contains("PAETH minimizes local residuals"));
    }

    @Test
    void detectsMatchQualityOverCount() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-up", diag(100, 20.0, 0.80, 600, 260, 0, 500, 0.80, usage(PngFilter.UP, 100)));
        d.put("fixed-sub", diag(102, 9.0, 0.50, 760, 180, 0, 510, 0.80, usage(PngFilter.SUB, 100)));
        String insight = generator.compressionInsight("fixed-up", d);
        assertTrue(insight.contains("match quality") || insight.contains("longer, better-aligned phrases"));
    }

    @Test
    void detectsMixedContentImages() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("genetic", diag(120, 8.0, 0.55, 700, 180, 0, 500, 1.0, mixedUsage(35, 25, 20, 20, 0)));
        d.put("fixed-paeth", diag(115, 7.5, 0.50, 730, 170, 0, 500, 1.0, usage(PngFilter.PAETH, 100)));
        String insight = generator.compressionInsight("genetic", d);
        assertTrue(insight.contains("competing local structures"));
    }

    @Test
    void classifiesGradients() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-up", diag(130, 10.0, 0.70, 650, 200, 0, 410, 0.60, usage(PngFilter.UP, 100)));
        d.put("fixed-paeth", diag(120, 8.0, 0.55, 720, 180, 0, 350, 0.60, usage(PngFilter.PAETH, 100)));
        String verbose = generator.compressionInsight("fixed-up", d, true);
        assertTrue(verbose.contains("directional measurements") || verbose.contains("directional structure"));
    }

    @Test
    void classifiesScreenshots() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-sub", diag(180, 10.0, 0.70, 640, 210, 0, 420, 1.05, usage(PngFilter.SUB, 100)));
        d.put("fixed-paeth", diag(175, 8.0, 0.58, 720, 180, 0, 390, 1.05, usage(PngFilter.PAETH, 100)));
        String verbose = generator.compressionInsight("fixed-sub", d, true);
        assertTrue(verbose.contains("mostly uniform predictor strategy"));
    }

    @Test
    void classifiesPainterlyImages() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("adaptive", diag(80, 5.0, 0.40, 880, 100, 0, 500, 1.0, mixedUsage(25, 25, 20, 15, 15)));
        d.put("fixed-paeth", diag(78, 5.2, 0.42, 900, 110, 0, 480, 1.0, usage(PngFilter.PAETH, 100)));
        String verbose = generator.compressionInsight("adaptive", d, true);
        assertTrue(verbose.contains("different image regions favor different prediction styles"));
    }

    @Test
    void classifiesLogosIcons() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-none", diagWithColor(6, 240, 18.0, 0.80, 620, 260, 10, 460, 1.0, usage(PngFilter.NONE, 100)));
        d.put("fixed-sub", diagWithColor(6, 220, 10.0, 0.65, 700, 190, 0, 420, 1.0, usage(PngFilter.SUB, 100)));
        String verbose = generator.compressionInsight("fixed-none", d, true);
        assertTrue(verbose.contains("Repeated rows") || verbose.contains("exact repeated row structure"));
    }

    @Test
    void verboseInsightRendersObservationsInsteadOfInternalPatternNames() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-sub", diag(200, 11.0, 0.75, 600, 230, 0, 400, 1.2, usage(PngFilter.SUB, 100)));
        d.put("fixed-paeth", diag(180, 8.0, 0.55, 720, 180, 0, 150, 1.2, usage(PngFilter.PAETH, 100)));

        String verbose = generator.compressionInsight("fixed-sub", d, true);

        assertTrue(verbose.contains("Compression observations:"));
        assertTrue(verbose.contains("PAETH minimizes local residuals"));
        assertFalse(verbose.contains("Patterns detected:"));
        assertFalse(verbose.contains("simpler-predictors-beat-paeth"));
        assertFalse(verbose.contains("consistency-dominates"));
        assertFalse(verbose.contains("image-behavior"));
    }

    @Test
    void equivalentGeneticWinnerIsAcknowledgedAndSkippedForComparison() {
        Map<String, FilteredStreamDiagnostics> d = new LinkedHashMap<>();
        d.put("fixed-up", fingerprint(diag(100, 10, .8, 600, 200, 0, 300, .7, usage(PngFilter.UP, 100)), "same"));
        d.put("genetic", fingerprint(diag(100, 10, .8, 600, 200, 0, 300, .7, usage(PngFilter.UP, 100)), "same"));
        d.put("entropy", fingerprint(diag(80, 8, .5, 800, 150, 0, 300, .7, mixedUsage(0, 50, 0, 50, 0)), "different"));

        String text = generator.metricExplanation("fixed-up", d, Map.of("fixed-up", 900L, "genetic", 900L, "entropy", 1000L));

        assertTrue(text.contains("genetic search converged to the same filter sequence as fixed-up"));
        assertFalse(text.contains("100 vs 100"));
        assertTrue(new ExplanationContext("fixed-up", d, Map.of("fixed-up", 900L, "genetic", 900L, "entropy", 1000L))
                .finalSizeRunnerUp().orElseThrow().getKey().equals("entropy"));
    }

    @Test
    void preservesDifferentStreamsWithMatchingAggregateDiagnosticsAsFinalSizeCandidates() {
        FilteredStreamDiagnostics winner = fingerprint(diag(100, 10, .8, 600, 200, 0, 300, .7,
                usage(PngFilter.UP, 100)), "winner-stream");
        FilteredStreamDiagnostics distinct = fingerprint(diag(100, 10, .8, 600, 200, 0, 300, .7,
                usage(PngFilter.UP, 100)), "different-stream");
        Map<String, FilteredStreamDiagnostics> diagnostics = new LinkedHashMap<>();
        diagnostics.put("fixed-up", winner);
        diagnostics.put("adaptive", distinct);

        ExplanationContext context = new ExplanationContext("fixed-up", diagnostics,
                Map.of("fixed-up", 900L, "adaptive", 901L));

        assertEquals("adaptive", context.finalSizeRunnerUp().orElseThrow().getKey());
    }

    @Test
    void usesFinalSizeRunnerUpThroughoutMetricExplanationWhenLzOrderDiffers() {
        Map<String, FilteredStreamDiagnostics> diagnostics = new LinkedHashMap<>();
        diagnostics.put("fixed-up", fingerprint(diag(140, 12, .8, 600, 200, 0, 300, .7,
                usage(PngFilter.UP, 100)), "winner"));
        diagnostics.put("entropy", fingerprint(diag(100, 9, .5, 700, 150, 0, 300, .7,
                mixedUsage(0, 50, 0, 50, 0)), "size-runner"));
        diagnostics.put("adaptive", fingerprint(diag(120, 11, .7, 610, 180, 0, 300, .7,
                mixedUsage(0, 60, 0, 40, 0)), "lz-runner"));

        String text = generator.metricExplanation("fixed-up", diagnostics,
                Map.of("fixed-up", 900L, "entropy", 910L, "adaptive", 950L));

        assertTrue(text.contains("Compared with entropy"));
        assertFalse(text.contains("Compared with adaptive"));
    }

    private static FilteredStreamDiagnostics fingerprint(FilteredStreamDiagnostics d, String fingerprint) {
        return new FilteredStreamDiagnostics(d.width(), d.height(), d.colorType(), d.bitDepth(), d.bytesPerPixel(),
                d.bytesPerRow(), d.streamLength(), d.entropy(), d.zeroByteCount(), d.zeroPercentage(),
                d.distinctByteValues(), d.longestIdenticalRun(), d.repeatedFullRowCount(), d.rowsEqualToPrevious(),
                d.mostCommonRowHashCount(), d.filterUsage(), d.repetitionMetrics(), d.lzParseDiagnostics(),
                d.directionalSmoothness(), d.residualDiagnostics(), fingerprint);
    }

    private static FilteredStreamDiagnostics diag(int repeated32, double avgMatchLen, double shortDistanceShare,
                                                  long lzCost, int longestMatch, int rowsEqualPrev, long paethResidual,
                                                  double ratio, FilterUsage usage) {
        return diagWithColor(6, repeated32, avgMatchLen, shortDistanceShare, lzCost, longestMatch, rowsEqualPrev, paethResidual, ratio, usage);
    }

    private static FilteredStreamDiagnostics diagWithColor(int colorType, int repeated32, double avgMatchLen, double shortDistanceShare,
                                                           long lzCost, int longestMatch, int rowsEqualPrev, long paethResidual,
                                                           double ratio, FilterUsage usage) {
        long[] distBuckets = new long[]{Math.round(shortDistanceShare * 100), 100 - Math.round(shortDistanceShare * 100), 0, 0, 0};
        long[] lengthBuckets = new long[6];
        lengthBuckets[4] = longestMatch >= 64 ? 1 : 0;
        LzParseDiagnostics lz = new LzParseDiagnostics(0, 100, 1000, 0, 100.0, avgMatchLen, longestMatch,
                lengthBuckets, distBuckets, lzCost, 0, 0, 0);
        return new FilteredStreamDiagnostics(16, 16, colorType, 8, 4, 64, 1000, 7.0, 30, 3.0, 190, 5, 0, rowsEqualPrev, 0, usage,
                new RepetitionMetrics(0, repeated32, 0, longestMatch, 0),
                lz,
                new DirectionalSmoothness(10.0, 10.0 * ratio, ratio),
                new ResidualDiagnostics(450, 420, 410, 430, paethResidual));
    }

    private static FilterUsage usage(PngFilter filter, int rows) {
        EnumMap<PngFilter, Integer> m = emptyUsage();
        m.put(filter, rows);
        return new FilterUsage(m, rows);
    }

    private static FilterUsage mixedUsage(int sub, int up, int average, int paeth, int none) {
        EnumMap<PngFilter, Integer> m = emptyUsage();
        m.put(PngFilter.SUB, sub);
        m.put(PngFilter.UP, up);
        m.put(PngFilter.AVERAGE, average);
        m.put(PngFilter.PAETH, paeth);
        m.put(PngFilter.NONE, none);
        return new FilterUsage(m, sub + up + average + paeth + none);
    }

    private static EnumMap<PngFilter, Integer> emptyUsage() {
        EnumMap<PngFilter, Integer> m = new EnumMap<>(PngFilter.class);
        for (PngFilter f : PngFilter.values()) m.put(f, 0);
        return m;
    }
}
