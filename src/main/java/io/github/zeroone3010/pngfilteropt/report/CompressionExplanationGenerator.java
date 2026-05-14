package io.github.zeroone3010.pngfilteropt.report;

import io.github.zeroone3010.pngfilteropt.diagnostics.FilteredStreamDiagnostics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class CompressionExplanationGenerator {
    private final List<ExplanationRule> rules = List.of(
            new ResidualConflictRule(),
            new DirectionalSmoothnessRule(),
            new RepeatedStructureRule(),
            new OutcomePriorityRule()
    );

    public String explain(String best, Map<String, FilteredStreamDiagnostics> diagnostics) {
        FilteredStreamDiagnostics winner = diagnostics.get(best);
        if (winner == null) return "Best strategy balances local and global compression heuristics.";

        ExplanationContext context = new ExplanationContext(best, winner, diagnostics);
        List<ExplanationObservation> observations = new ArrayList<>();
        for (ExplanationRule rule : rules) {
            observations.addAll(rule.apply(context));
        }
        observations.sort(Comparator.comparingInt(ExplanationObservation::priority).reversed());
        if (observations.isEmpty()) return "Best strategy balances local and global compression heuristics.";
        return observations.stream().map(ExplanationObservation::message).distinct().limit(4).reduce((a, b) -> a + " " + b).orElse("Best strategy balances local and global compression heuristics.");
    }

    interface ExplanationRule {
        List<ExplanationObservation> apply(ExplanationContext context);
    }

    record ExplanationContext(String best, FilteredStreamDiagnostics winner, Map<String, FilteredStreamDiagnostics> diagnostics) {
        String bestResidualFilter() {
            var r = winner.residualDiagnostics();
            long min = Math.min(Math.min(Math.min(r.noneSumAbs(), r.subSumAbs()), Math.min(r.upSumAbs(), r.averageSumAbs())), r.paethSumAbs());
            if (min == r.noneSumAbs()) return "NONE";
            if (min == r.subSumAbs()) return "SUB";
            if (min == r.upSumAbs()) return "UP";
            if (min == r.averageSumAbs()) return "AVERAGE";
            return "PAETH";
        }
    }

    record ExplanationObservation(int priority, String message) {
    }

    static final class ResidualConflictRule implements ExplanationRule {
        @Override
        public List<ExplanationObservation> apply(ExplanationContext context) {
            List<ExplanationObservation> out = new ArrayList<>();
            String localBest = context.bestResidualFilter();
            if (!context.best().toUpperCase().contains(localBest) && "PAETH".equals(localBest)) {
                out.add(new ExplanationObservation(100, "Residual diagnostics favor PAETH locally, but the selected strategy appears to improve globally repeatable byte patterns for DEFLATE."));
            } else if (context.best().contains("paeth") && "PAETH".equals(localBest)) {
                out.add(new ExplanationObservation(65, "Residual diagnostics and final outcome are aligned: PAETH minimizes local residual magnitude and remains competitive globally."));
            }
            return out;
        }
    }

    static final class DirectionalSmoothnessRule implements ExplanationRule {
        @Override
        public List<ExplanationObservation> apply(ExplanationContext context) {
            double ratio = context.winner().directionalSmoothness().verticalHorizontalRatio();
            if (ratio < 0.85) return List.of(new ExplanationObservation(55, "Vertical smoothness exceeds horizontal smoothness, which aligns with UP-style prediction performing well."));
            if (ratio > 1.20) return List.of(new ExplanationObservation(55, "Horizontal coherence is stronger than vertical coherence, which can benefit SUB-style prediction."));
            return List.of(new ExplanationObservation(35, "Directional smoothness is relatively balanced, making PAETH-like local prediction plausible."));
        }
    }

    static final class RepeatedStructureRule implements ExplanationRule {
        @Override
        public List<ExplanationObservation> apply(ExplanationContext context) {
            List<ExplanationObservation> out = new ArrayList<>();
            var winner = context.winner();
            var rm = winner.repetitionMetrics();
            int maxRep32 = context.diagnostics().values().stream().mapToInt(x -> x.repetitionMetrics().repeated32ByteSubstrings()).max().orElse(0);
            int maxRows = context.diagnostics().values().stream().mapToInt(FilteredStreamDiagnostics::rowsEqualToPrevious).max().orElse(0);
            if (rm.repeated32ByteSubstrings() == maxRep32 && maxRep32 > 0) {
                out.add(new ExplanationObservation(95, "The winning strategy produces the strongest repeated DEFLATE-friendly substring structure, increasing repeated LZ-style matches."));
            }
            if (winner.rowsEqualToPrevious() == maxRows && maxRows > 0 && context.best().equals("fixed-none")) {
                out.add(new ExplanationObservation(85, "NONE preserves literal row repetition, which can improve downstream DEFLATE matching despite weaker local residual metrics."));
            }
            if (winner.repetitionMetrics().longest32KiBMatch() >= context.diagnostics().values().stream().mapToInt(x -> x.repetitionMetrics().longest32KiBMatch()).max().orElse(0)) {
                out.add(new ExplanationObservation(75, "Longest repeated match diagnostics indicate strong global back-reference opportunities in the winning stream."));
            }
            return out;
        }
    }

    static final class OutcomePriorityRule implements ExplanationRule {
        @Override
        public List<ExplanationObservation> apply(ExplanationContext context) {
            String localBest = context.bestResidualFilter();
            if ("PAETH".equals(localBest) && !context.best().contains("paeth")) {
                return List.of(new ExplanationObservation(90, "Global repetition appears to outweigh local residual minimization for final compression size."));
            }
            return List.of(new ExplanationObservation(30, "Final compression outcome likely reflects a tradeoff between local predictor precision and global DEFLATE structure."));
        }
    }
}
