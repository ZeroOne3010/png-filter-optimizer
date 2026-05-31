package io.github.zeroone3010.pngfilteropt.report;

import io.github.zeroone3010.pngfilteropt.diagnostics.FilteredStreamDiagnostics;

import java.util.Map;

public final class MarkdownDiagnosticsRenderer {
    private final CompressionExplanationGenerator explanationGenerator = new CompressionExplanationGenerator();

    public void appendDiagnostics(StringBuilder sb, Map<String, Object> image, boolean verboseInsights) {
        @SuppressWarnings("unchecked") Map<String, FilteredStreamDiagnostics> diagnostics = (Map<String, FilteredStreamDiagnostics>) image.get("diagnostics");
        if (diagnostics == null || diagnostics.isEmpty()) return;

        String best = (String) image.get("best");
        sb.append("Likely explanation:\n")
                .append(explanationGenerator.metricExplanation(best, diagnostics))
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

    private void appendLzTable(StringBuilder sb, Map<String, FilteredStreamDiagnostics> diagnostics) {
        sb.append("| Strategy | LZ cost bits | Match coverage % | Avg match len | Matches 64+ | Short dist % | Long dist % |\n|---|---:|---:|---:|---:|---:|---:|\n");
        for (var e : diagnostics.entrySet()) {
            var lz = e.getValue().lzParseDiagnostics();
            long matches64 = lz.matchLengthBuckets()[4] + lz.matchLengthBuckets()[5];
            long distTotal = java.util.Arrays.stream(lz.matchDistanceBuckets()).sum();
            double shortPct = distTotal == 0 ? 0 : (100.0 * lz.matchDistanceBuckets()[0] / distTotal);
            double longPct = distTotal == 0 ? 0 : (100.0 * lz.matchDistanceBuckets()[4] / distTotal);
            sb.append("| ").append(e.getKey()).append(" | ").append(lz.approximateLzCostBits()).append(" | ").append(String.format("%.1f", lz.matchCoveragePercent())).append(" | ").append(String.format("%.1f", lz.averageMatchLength())).append(" | ").append(matches64).append(" | ").append(String.format("%.1f", shortPct)).append(" | ").append(String.format("%.1f", longPct)).append(" |\n");
        }
    }

    private void appendDirectionalSmoothness(StringBuilder sb, FilteredStreamDiagnostics diagnostics) {
        sb.append("\nDirectional smoothness:\n\n| Metric | Value |\n|---|---:|\n");
        sb.append("| Mean horizontal delta | ").append(String.format("%.2f", diagnostics.directionalSmoothness().meanHorizontalDelta())).append(" |\n");
        sb.append("| Mean vertical delta | ").append(String.format("%.2f", diagnostics.directionalSmoothness().meanVerticalDelta())).append(" |\n");
        sb.append("| Vertical/Horizontal ratio | ").append(String.format("%.2f", diagnostics.directionalSmoothness().verticalHorizontalRatio())).append(" |\n");
    }

    private void appendResidualSumAbs(StringBuilder sb, FilteredStreamDiagnostics diagnostics) {
        sb.append("\nResidual sumAbs:\n\n| Filter | SumAbs |\n|---|---:|\n");
        sb.append("| NONE | ").append(diagnostics.residualDiagnostics().noneSumAbs()).append(" |\n");
        sb.append("| SUB | ").append(diagnostics.residualDiagnostics().subSumAbs()).append(" |\n");
        sb.append("| UP | ").append(diagnostics.residualDiagnostics().upSumAbs()).append(" |\n");
        sb.append("| AVERAGE | ").append(diagnostics.residualDiagnostics().averageSumAbs()).append(" |\n");
        sb.append("| PAETH | ").append(diagnostics.residualDiagnostics().paethSumAbs()).append(" |\n");
    }
}
