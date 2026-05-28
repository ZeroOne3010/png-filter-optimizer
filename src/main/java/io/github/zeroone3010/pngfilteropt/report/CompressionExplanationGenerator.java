package io.github.zeroone3010.pngfilteropt.report;

import io.github.zeroone3010.pngfilteropt.diagnostics.FilteredStreamDiagnostics;

import java.util.Map;

public final class CompressionExplanationGenerator {
    private final InsightSynthesizer insightSynthesizer = new InsightSynthesizer();
    private final ExplanationRenderer renderer = new ExplanationRenderer();

    public String explain(String best, Map<String, FilteredStreamDiagnostics> diagnostics) {
        return metricExplanation(best, diagnostics);
    }

    public String metricExplanation(String best, Map<String, FilteredStreamDiagnostics> diagnostics) {
        return renderer.renderMetricExplanation(new ExplanationContext(best, diagnostics));
    }

    public String compressionInsight(String best, Map<String, FilteredStreamDiagnostics> diagnostics) {
        return compressionInsight(best, diagnostics, false);
    }

    public String compressionInsight(String best, Map<String, FilteredStreamDiagnostics> diagnostics, boolean verbose) {
        ExplanationContext context = new ExplanationContext(best, diagnostics);
        return renderer.renderInsight(insightSynthesizer.synthesize(context), verbose);
    }

    public CompressionInsight synthesizeInsight(String best, Map<String, FilteredStreamDiagnostics> diagnostics) {
        return insightSynthesizer.synthesize(new ExplanationContext(best, diagnostics));
    }
}
