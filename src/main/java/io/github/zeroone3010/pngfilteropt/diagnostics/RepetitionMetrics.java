package io.github.zeroone3010.pngfilteropt.diagnostics;

public record RepetitionMetrics(
        int repeated16ByteSubstrings,
        int repeated32ByteSubstrings,
        int repeated64ByteSubstrings,
        int longest32KiBMatch,
        long greedyEstimatedSavings
) {
}
