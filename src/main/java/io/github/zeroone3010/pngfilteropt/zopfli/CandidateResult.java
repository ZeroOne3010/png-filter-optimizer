package io.github.zeroone3010.pngfilteropt.zopfli;

import io.github.zeroone3010.pngfilteropt.filter.PngFilter;

public record CandidateResult(PngFilter filter, int estimatedCost) {
}
