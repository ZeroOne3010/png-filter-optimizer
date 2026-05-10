package io.github.zeroone3010.pngfilteropt.zopfli;

import java.util.Comparator;
import java.util.List;

public class ZopfliRunner {
    public OptimizationReport evaluate(List<CandidateResult> candidates) {
        CandidateResult best = candidates.stream().min(Comparator.comparingInt(CandidateResult::estimatedCost)).orElse(null);
        return new OptimizationReport(candidates, best);
    }
}
