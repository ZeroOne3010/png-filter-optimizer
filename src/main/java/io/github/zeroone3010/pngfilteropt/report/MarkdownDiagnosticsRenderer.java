package io.github.zeroone3010.pngfilteropt.report;

import io.github.zeroone3010.pngfilteropt.diagnostics.FilteredStreamDiagnostics;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MarkdownDiagnosticsRenderer {
    private final CompressionExplanationGenerator explanationGenerator = new CompressionExplanationGenerator();

    public void appendDiagnostics(StringBuilder sb, Map<String, Object> image, boolean verboseInsights) {
        @SuppressWarnings("unchecked") Map<String, FilteredStreamDiagnostics> diagnostics = (Map<String, FilteredStreamDiagnostics>) image.get("diagnostics");
        if (diagnostics == null || diagnostics.isEmpty()) return;

        String best = (String) image.get("best");
        sb.append("Likely explanation:\n");
        outcomeSummary(image).ifPresent(summary -> sb.append(summary).append('\n'));
        sb.append(explanationGenerator.metricExplanation(best, diagnostics))
                .append("\n\nCompression insight:\n")
                .append(explanationGenerator.compressionInsight(best, diagnostics, verboseInsights))
                .append("\n\n");

        sb.append("<details>\n<summary>Diagnostics</summary>\n\n");
        sb.append("_Note: diagnostics include approximate LZ longest-match estimation (sampled hash-chain over 32 KiB lookback)._\n\n");
        appendLzTable(sb, diagnostics);
        appendDirectionalSmoothness(sb, diagnostics.values().iterator().next());
        appendResidualSumAbs(sb, diagnostics.values().iterator().next());
        sb.append("\n</details>\n\n");
    }


    static Optional<String> outcomeSummary(Map<String, Object> image) {
        Object bestObject = image.get("best");
        if (!(bestObject instanceof String best) || best.isBlank()) return Optional.empty();

        @SuppressWarnings("unchecked") Map<String, Long> sizes = (Map<String, Long>) image.get("strategies");
        if (sizes == null || !sizes.containsKey(best)) return Optional.empty();

        long bestSize = sizes.get(best);
        return comparisonStrategy(sizes, best, bestSize)
                .map(runner -> formatOutcomeSummary(best, bestSize, runner.getKey(), runner.getValue()));
    }

    private static Optional<Map.Entry<String, Long>> comparisonStrategy(Map<String, Long> sizes, String best, long bestSize) {
        return sizes.entrySet().stream()
                .filter(e -> !e.getKey().startsWith("delta-"))
                .filter(e -> !e.getKey().equals(best))
                .filter(e -> !("genetic".equals(e.getKey()) && e.getValue() == bestSize))
                .min(Comparator.comparingLong(Map.Entry<String, Long>::getValue).thenComparing(Map.Entry::getKey));
    }

    private static String formatOutcomeSummary(String best, long bestSize, String runner, long runnerSize) {
        long byteMargin = runnerSize - bestSize;
        if (byteMargin == 0) {
            return "Winning strategy: `" + best + "` tied `" + runner + "` at " + formatNumber(bestSize) + " bytes after ignoring same-size genetic duplicates.";
        }

        double percentSmaller = runnerSize <= 0 ? 0.0 : 100.0 * byteMargin / runnerSize;
        String sizeLabel = marginLabel(percentSmaller);
        String direction = byteMargin > 0 ? "beat" : "trailed";
        long absoluteMargin = Math.abs(byteMargin);
        double absolutePercent = Math.abs(percentSmaller);
        return String.format(Locale.ROOT,
                "Winning strategy: `%s` %s `%s` by a %s margin of %s bytes (%.2f%% smaller).",
                best, direction, runner, sizeLabel, formatNumber(absoluteMargin), absolutePercent);
    }

    private static String marginLabel(double percentSmaller) {
        double absolute = Math.abs(percentSmaller);
        if (absolute < 1.0) return "tiny";
        if (absolute < 3.0) return "small";
        if (absolute < 8.0) return "moderate";
        return "large";
    }

    private void appendLzTable(StringBuilder sb, Map<String, FilteredStreamDiagnostics> diagnostics) {
        sb.append("| Strategy | LZ cost bits | Match coverage % | Avg match len | Matches 64+ | Short dist % | Long dist % |\n|---|---:|---:|---:|---:|---:|---:|\n");
        for (var e : diagnostics.entrySet()) {
            var lz = e.getValue().lzParseDiagnostics();
            long matches64 = lz.matchLengthBuckets()[4] + lz.matchLengthBuckets()[5];
            long distTotal = java.util.Arrays.stream(lz.matchDistanceBuckets()).sum();
            double shortPct = distTotal == 0 ? 0 : (100.0 * lz.matchDistanceBuckets()[0] / distTotal);
            double longPct = distTotal == 0 ? 0 : (100.0 * lz.matchDistanceBuckets()[4] / distTotal);
            sb.append("| ").append(e.getKey()).append(" | ").append(formatNumber(lz.approximateLzCostBits())).append(" | ").append(String.format("%.1f", lz.matchCoveragePercent())).append(" | ").append(String.format("%.1f", lz.averageMatchLength())).append(" | ").append(matches64).append(" | ").append(String.format("%.1f", shortPct)).append(" | ").append(String.format("%.1f", longPct)).append(" |\n");
        }
    }

    private void appendDirectionalSmoothness(StringBuilder sb, FilteredStreamDiagnostics diagnostics) {
        sb.append("\nDirectional smoothness:\n\n| Metric | Value |\n|---|---:|\n");
        sb.append("| Mean horizontal delta | ").append(String.format("%.2f", diagnostics.directionalSmoothness().meanHorizontalDelta())).append(" |\n");
        sb.append("| Mean vertical delta | ").append(String.format("%.2f", diagnostics.directionalSmoothness().meanVerticalDelta())).append(" |\n");
        sb.append("| Vertical/Horizontal ratio | ").append(String.format("%.2f", diagnostics.directionalSmoothness().verticalHorizontalRatio())).append(" |\n");
    }

    static String formatNumber(long value) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) out.append('\u2007');
            out.append(digits.charAt(i));
        }
        return value < 0 ? "-" + out : out.toString();
    }

    private void appendResidualSumAbs(StringBuilder sb, FilteredStreamDiagnostics diagnostics) {
        sb.append("\nResidual sumAbs:\n\n| Filter | SumAbs |\n|---|---:|\n");
        sb.append("| NONE | ").append(formatNumber(diagnostics.residualDiagnostics().noneSumAbs())).append(" |\n");
        sb.append("| SUB | ").append(formatNumber(diagnostics.residualDiagnostics().subSumAbs())).append(" |\n");
        sb.append("| UP | ").append(formatNumber(diagnostics.residualDiagnostics().upSumAbs())).append(" |\n");
        sb.append("| AVERAGE | ").append(formatNumber(diagnostics.residualDiagnostics().averageSumAbs())).append(" |\n");
        sb.append("| PAETH | ").append(formatNumber(diagnostics.residualDiagnostics().paethSumAbs())).append(" |\n");
    }
}
