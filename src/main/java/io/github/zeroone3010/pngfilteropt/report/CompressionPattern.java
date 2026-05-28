package io.github.zeroone3010.pngfilteropt.report;

public record CompressionPattern(Type type, String description, double strength) {
    public enum Type {
        CONSISTENCY_DOMINATES,
        SIMPLER_PREDICTORS_BEAT_PAETH,
        LITERAL_STRUCTURE_PRESERVATION,
        USEFUL_LOCAL_EXCEPTIONS,
        MATCH_QUALITY_DOMINATES,
        DIRECTIONAL_COHERENCE,
        MIXED_CONTENT,
        IMAGE_BEHAVIOR
    }
}
