package io.github.zeroone3010.pngfilteropt.report;

import io.github.zeroone3010.pngfilteropt.diagnostics.FilteredStreamDiagnostics;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;

import java.util.List;
import java.util.Map;

public final class MarkdownDiagnosticsRenderer {
    private final CompressionExplanationGenerator explanationGenerator = new CompressionExplanationGenerator();
    public String render(List<Map<String, Object>> images) {
        StringBuilder sb = new StringBuilder("\n## Diagnostics\n\n");
        sb.append("_Note: diagnostics include approximate LZ longest-match estimation (sampled hash-chain over 32 KiB lookback)._\n\n");
        for (Map<String, Object> image : images) {
            sb.append("### ").append(image.get("image")).append("\n\n");
            sb.append("Best strategy: ").append(image.get("best")).append("\n\n");
            @SuppressWarnings("unchecked") Map<String, FilteredStreamDiagnostics> d = (Map<String, FilteredStreamDiagnostics>) image.get("diagnostics");
            if (d == null || d.isEmpty()) continue;
            var any = d.values().iterator().next();
            sb.append("PNG metadata: width=").append(any.width()).append(", height=").append(any.height()).append(", colorType=").append(any.colorType()).append(", bitDepth=").append(any.bitDepth()).append(", bytesPerPixel=").append(any.bytesPerPixel()).append(", bytesPerRow=").append(any.bytesPerRow()).append("\n\n");
            sb.append("| Strategy | LZ cost bits | Match coverage % | Avg match len | Matches 64+ | Short dist % | Long dist % |\n|---|---:|---:|---:|---:|---:|---:|\n");
            for (var e : d.entrySet()) {
                var m = e.getValue();
                var lz = m.lzParseDiagnostics();
                long matches64 = lz.matchLengthBuckets()[4] + lz.matchLengthBuckets()[5];
                long distTotal = java.util.Arrays.stream(lz.matchDistanceBuckets()).sum();
                double shortPct = distTotal == 0 ? 0 : (100.0 * lz.matchDistanceBuckets()[0] / distTotal);
                double longPct = distTotal == 0 ? 0 : (100.0 * lz.matchDistanceBuckets()[4] / distTotal);
                sb.append("| ").append(e.getKey()).append(" | ").append(lz.approximateLzCostBits()).append(" | ").append(String.format("%.1f", lz.matchCoveragePercent())).append(" | ").append(String.format("%.1f", lz.averageMatchLength())).append(" | ").append(matches64).append(" | ").append(String.format("%.1f", shortPct)).append(" | ").append(String.format("%.1f", longPct)).append(" |\n");
            }
            sb.append("\nFilter distribution:\n\n| Strategy | NONE | SUB | UP | AVERAGE | PAETH |\n|---|---:|---:|---:|---:|---:|\n");
            for (var e : d.entrySet()) {
                sb.append("| ").append(e.getKey());
                for (PngFilter f : PngFilter.values()) sb.append(" | ").append(e.getValue().filterUsage().counts().get(f));
                sb.append(" |\n");
            }

            sb.append("\n## Directional smoothness\n\n| Metric | Value |\n|---|---:|\n");
            sb.append("| Mean horizontal delta | ").append(String.format("%.2f", any.directionalSmoothness().meanHorizontalDelta())).append(" |\n");
            sb.append("| Mean vertical delta | ").append(String.format("%.2f", any.directionalSmoothness().meanVerticalDelta())).append(" |\n");
            sb.append("| Vertical/Horizontal ratio | ").append(String.format("%.2f", any.directionalSmoothness().verticalHorizontalRatio())).append(" |\n");

            sb.append("\n## Residual sumAbs\n\n| Filter | SumAbs |\n|---|---:|\n");
            sb.append("| NONE | ").append(any.residualDiagnostics().noneSumAbs()).append(" |\n");
            sb.append("| SUB | ").append(any.residualDiagnostics().subSumAbs()).append(" |\n");
            sb.append("| UP | ").append(any.residualDiagnostics().upSumAbs()).append(" |\n");
            sb.append("| AVERAGE | ").append(any.residualDiagnostics().averageSumAbs()).append(" |\n");
            sb.append("| PAETH | ").append(any.residualDiagnostics().paethSumAbs()).append(" |\n");

            sb.append("\nLikely explanation: ").append(explanationGenerator.explain((String) image.get("best"), d)).append("\n\n");
        }
        return sb.toString();
    }

}
