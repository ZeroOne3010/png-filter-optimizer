package io.github.zeroone3010.pngfilteropt.report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Converts internal pattern identifiers into reader-facing compression observations. */
public final class PatternObservationRenderer {
    private static final int MAX_OBSERVATIONS = 4;

    public List<String> render(List<CompressionPattern> patterns) {
        List<CompressionPattern> ranked = patterns.stream()
                .filter(pattern -> relevance(pattern.type()) > 0)
                .sorted(Comparator.comparingDouble(CompressionPattern::strength).reversed()
                        .thenComparing(Comparator.comparingInt((CompressionPattern pattern) -> relevance(pattern.type())).reversed()))
                .toList();

        List<String> observations = new ArrayList<>();
        Set<String> topics = new HashSet<>();
        for (CompressionPattern pattern : ranked) {
            if (!topics.add(topic(pattern.type()))) continue;
            observations.add(render(pattern));
            if (observations.size() == MAX_OBSERVATIONS) break;
        }
        return observations;
    }

    private String render(CompressionPattern pattern) {
        String signal = signal(pattern.strength());
        return switch (pattern.type()) {
            case LITERAL_STRUCTURE_PRESERVATION ->
                    "Repeated rows " + signal + " preserving exact bytes creates more reusable DEFLATE phrases than minimizing each residual locally.";
            case SIMPLER_PREDICTORS_BEAT_PAETH ->
                    "PAETH minimizes local residuals, but the result " + signal + " simpler predictors create a more reusable DEFLATE vocabulary than its locally adaptive predictor.";
            case CONSISTENCY_DOMINATES ->
                    "The result " + signal + " a mostly uniform predictor strategy is more valuable than locally optimal prediction because it keeps the residual vocabulary stable.";
            case USEFUL_LOCAL_EXCEPTIONS ->
                    "The filter distribution " + signal + " a stable predictor with a few localized exceptions preserves reusable phrases while accommodating regional changes.";
            case MATCH_QUALITY_DOMINATES ->
                    "The LZ diagnostics " + signal + " longer or better-aligned matches matter more than the raw number of repeated substrings.";
            case DIRECTIONAL_COHERENCE ->
                    "The directional measurements " + signal + " scanline or column coherence is shaping which residual stream DEFLATE can reuse most effectively.";
            case MIXED_CONTENT ->
                    "The filter distribution " + signal + " different image regions favor different prediction styles, with competing local structures preventing one residual language from dominating.";
            case IMAGE_BEHAVIOR -> throw new IllegalArgumentException("Image behavior is contextual metadata, not a reader-facing observation");
        };
    }

    private String signal(double strength) {
        if (strength >= 0.85) return "provides clear evidence that";
        if (strength >= 0.65) return "suggests that";
        return "may indicate that";
    }

    private int relevance(CompressionPattern.Type type) {
        return switch (type) {
            case SIMPLER_PREDICTORS_BEAT_PAETH, LITERAL_STRUCTURE_PRESERVATION, MATCH_QUALITY_DOMINATES -> 3;
            case CONSISTENCY_DOMINATES, USEFUL_LOCAL_EXCEPTIONS, MIXED_CONTENT, DIRECTIONAL_COHERENCE -> 2;
            case IMAGE_BEHAVIOR -> 0;
        };
    }

    private String topic(CompressionPattern.Type type) {
        return switch (type) {
            case SIMPLER_PREDICTORS_BEAT_PAETH, CONSISTENCY_DOMINATES -> "residual-vocabulary";
            case LITERAL_STRUCTURE_PRESERVATION -> "literal-structure";
            case USEFUL_LOCAL_EXCEPTIONS -> "local-exceptions";
            case MATCH_QUALITY_DOMINATES -> "match-quality";
            case DIRECTIONAL_COHERENCE -> "directionality";
            case MIXED_CONTENT -> "mixed-content";
            case IMAGE_BEHAVIOR -> "image-behavior";
        };
    }
}
