package io.github.zeroone3010.pngfilteropt.report;

import io.github.zeroone3010.pngfilteropt.diagnostics.FilteredStreamDiagnostics;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;

import java.util.List;
import java.util.Map;

public final class MarkdownDiagnosticsRenderer {
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
            sb.append("| Strategy | Entropy | Zero % | Distinct bytes | Longest run | Prev-row repeats | Repeated 32B substrings | Longest 32KiB match |\n|---|---:|---:|---:|---:|---:|---:|---:|\n");
            for (var e : d.entrySet()) {
                var m = e.getValue();
                sb.append("| ").append(e.getKey()).append(" | ").append(String.format("%.3f", m.entropy())).append(" | ").append(String.format("%.1f", m.zeroPercentage())).append(" | ").append(m.distinctByteValues()).append(" | ").append(m.longestIdenticalRun()).append(" | ").append(m.rowsEqualToPrevious()).append(" | ").append(m.repetitionMetrics().repeated32ByteSubstrings()).append(" | ").append(m.repetitionMetrics().longest32KiBMatch()).append(" |\n");
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

            sb.append("\nLikely explanation: ").append(explain((String) image.get("best"), d)).append("\n\n");
        }
        return sb.toString();
    }

    private String explain(String best, Map<String, FilteredStreamDiagnostics> d) {
        var b = d.get(best);
        if (b == null) return "Best strategy balances entropy and repetition heuristics.";
        double ratio = b.directionalSmoothness().verticalHorizontalRatio();
        var r = b.residualDiagnostics();
        if (best.equals("fixed-none") && b.repetitionMetrics().repeated32ByteSubstrings() > 0) return "NONE preserves repeated row structures despite higher entropy, improving downstream DEFLATE matches.";
        if (ratio < 0.8) return "Vertical smoothness is stronger than horizontal smoothness, which likely favors UP-style prediction.";
        if (ratio > 1.25) return "Horizontal coherence dominates vertical coherence, making SUB-style prediction effective.";
        long minResidual = Math.min(Math.min(Math.min(r.noneSumAbs(), r.subSumAbs()), Math.min(r.upSumAbs(), r.averageSumAbs())), r.paethSumAbs());
        if (minResidual == r.paethSumAbs()) return "Residual diagnostics favor PAETH, suggesting mixed local smoothness where Paeth's predictor is robust.";
        if (best.equals("entropy")) return "entropy likely wins because it lowers byte entropy without giving up too much repetition structure.";
        return best + " likely wins from a favorable tradeoff between entropy, directional smoothness, and residual signals.";
    }
}
