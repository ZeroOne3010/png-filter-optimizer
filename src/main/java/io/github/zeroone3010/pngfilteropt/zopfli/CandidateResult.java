package io.github.zeroone3010.pngfilteropt.zopfli;

import io.github.zeroone3010.pngfilteropt.optimize.FilterOptimizer;

public record CandidateResult(FilterOptimizer optimizer, long outputSizeBytes) {
}
