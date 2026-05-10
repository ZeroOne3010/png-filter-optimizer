package io.github.zeroone3010.pngfilteropt.zopfli;

import java.util.List;

public record OptimizationReport(List<CandidateResult> candidates, CandidateResult best) {
}
