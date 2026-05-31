package io.github.zeroone3010.pngfilteropt.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternObservationRendererTest {
    private final PatternObservationRenderer renderer = new PatternObservationRenderer();

    @Test
    void rendersResearcherFriendlyObservationsWithoutInternalLabels() {
        List<String> observations = renderer.render(List.of(
                pattern(CompressionPattern.Type.SIMPLER_PREDICTORS_BEAT_PAETH, 0.85),
                pattern(CompressionPattern.Type.MIXED_CONTENT, 0.65)));

        assertEquals(2, observations.size());
        assertTrue(observations.get(0).contains("PAETH minimizes local residuals"));
        assertTrue(observations.get(0).contains("clear evidence"));
        assertTrue(observations.get(1).contains("different image regions favor different prediction styles"));
        assertFalse(String.join(" ", observations).contains("simpler-predictors-beat-paeth"));
        assertFalse(String.join(" ", observations).contains("mixed-content"));
    }

    @Test
    void confidenceChangesWording() {
        assertTrue(renderer.render(List.of(pattern(CompressionPattern.Type.MATCH_QUALITY_DOMINATES, 0.50)))
                .getFirst().contains("may indicate"));
        assertTrue(renderer.render(List.of(pattern(CompressionPattern.Type.MATCH_QUALITY_DOMINATES, 0.70)))
                .getFirst().contains("suggests"));
        assertTrue(renderer.render(List.of(pattern(CompressionPattern.Type.MATCH_QUALITY_DOMINATES, 0.90)))
                .getFirst().contains("clear evidence"));
    }

    @Test
    void ranksLimitsAndDeduplicatesObservations() {
        List<String> observations = renderer.render(List.of(
                pattern(CompressionPattern.Type.DIRECTIONAL_COHERENCE, 0.65),
                pattern(CompressionPattern.Type.USEFUL_LOCAL_EXCEPTIONS, 0.70),
                pattern(CompressionPattern.Type.MATCH_QUALITY_DOMINATES, 0.75),
                pattern(CompressionPattern.Type.CONSISTENCY_DOMINATES, 0.90),
                pattern(CompressionPattern.Type.SIMPLER_PREDICTORS_BEAT_PAETH, 0.85),
                pattern(CompressionPattern.Type.LITERAL_STRUCTURE_PRESERVATION, 0.95),
                pattern(CompressionPattern.Type.MIXED_CONTENT, 0.80)));

        assertEquals(4, observations.size());
        assertTrue(observations.getFirst().startsWith("Repeated rows"));
        assertTrue(observations.stream().anyMatch(text -> text.contains("mostly uniform predictor strategy")));
        assertFalse(observations.stream().anyMatch(text -> text.contains("PAETH minimizes local residuals")));
    }

    @Test
    void omitsContextOnlyImageBehavior() {
        var patterns = List.of(pattern(CompressionPattern.Type.IMAGE_BEHAVIOR, 0.90));

        assertTrue(renderer.render(patterns).isEmpty());
        assertEquals("Summary", new ExplanationRenderer().renderInsight(new CompressionInsight(patterns, "Summary"), true));
    }

    private CompressionPattern pattern(CompressionPattern.Type type, double strength) {
        return new CompressionPattern(type, "internal debugging description", strength);
    }
}
