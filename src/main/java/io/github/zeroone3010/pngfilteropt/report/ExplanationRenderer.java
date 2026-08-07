package io.github.zeroone3010.pngfilteropt.report;

import io.github.zeroone3010.pngfilteropt.diagnostics.FilteredStreamDiagnostics;
import io.github.zeroone3010.pngfilteropt.diagnostics.LzParseDiagnostics;

import java.util.Locale;
import java.util.Map;

public final class ExplanationRenderer {
    private static final String DEFAULT_MESSAGE = "Compression outcome reflects a close tradeoff between local predictor precision and global DEFLATE repetition.";
    private static final double SMALL_REPETITION_DELTA = 0.05;
    private static final double LARGE_REPETITION_DELTA = 0.20;
    private final PatternObservationRenderer observationRenderer = new PatternObservationRenderer();

    public String renderMetricExplanation(ExplanationContext context) {
        FilteredStreamDiagnostics winner = context.winner();
        if (winner == null) return DEFAULT_MESSAGE;
        String localBest = context.localBestFilter();
        String local = localSentence(localBest, winner, context.best());
        String equivalence = equivalenceSentence(context);
        Map.Entry<String, FilteredStreamDiagnostics> comparison = context.finalSizeRunnerUp().orElse(null);
        String global = globalSentence(context.best(), localBest, winner, context, comparison);
        String deflate = deflateSentence(context.best(), localBest, winner, context, comparison);
        return String.join(" ", local, equivalence, global, deflate).replaceAll(" +", " ").trim();
    }

    public String renderInsight(CompressionInsight insight, boolean verbose) {
        if (!verbose) return insight.summary();
        var observations = observationRenderer.render(insight.patterns());
        if (observations.isEmpty()) return insight.summary();

        StringBuilder sb = new StringBuilder(insight.summary());
        sb.append("\n\nCompression observations:\n");
        for (String observation : observations) {
            sb.append("- ").append(observation).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private String localSentence(String localBest, FilteredStreamDiagnostics winner, String best) {
        String direction = directionalClause(winner.directionalSmoothness().verticalHorizontalRatio(), best);
        if ("PAETH".equals(localBest)) {
            return "PAETH minimizes local residual magnitude." + direction;
        }
        return localBest + " minimizes local residual magnitude." + direction;
    }

    private String directionalClause(double ratio, String best) {
        if (ratio > 1.20) {
            return best.contains("sub")
                    ? " This image is strongly horizontally coherent, making SUB-style prediction particularly effective."
                    : " Horizontal smoothness is stronger than vertical smoothness.";
        }
        if (ratio < 0.85) {
            return best.contains("up")
                    ? " This image is strongly vertically coherent, making UP-style prediction particularly effective."
                    : " Vertical smoothness is stronger than horizontal smoothness.";
        }
        return " Directional smoothness is balanced across axes.";
    }

    private String globalSentence(String best, String localBest, FilteredStreamDiagnostics winner,
                                  ExplanationContext context,
                                  Map.Entry<String, FilteredStreamDiagnostics> comparison) {
        if ("fixed-none".equals(best) && winner.rowsEqualToPrevious() > 0) {
            return "However, fixed-none preserves literal row structure with " + winner.rowsEqualToPrevious() + " repeated rows, reinforcing exact repeated byte runs for DEFLATE back-references.";
        }

        FilteredStreamDiagnostics runner = comparison == null ? null : comparison.getValue();
        if (runner == null) {
            return "However, " + best + " wins on global stream structure rather than a single dominant local metric.";
        }

        int winnerRep32 = winner.repetitionMetrics().repeated32ByteSubstrings();
        int runnerRep32 = runner.repetitionMetrics().repeated32ByteSubstrings();
        double repDelta = relativeDelta(winnerRep32, runnerRep32);

        LzParseDiagnostics winnerLz = winner.lzParseDiagnostics();
        LzParseDiagnostics runnerLz = runner.lzParseDiagnostics();

        boolean betterCost = winnerLz.approximateLzCostBits() < runnerLz.approximateLzCostBits();
        boolean betterLength = winnerLz.averageMatchLength() > runnerLz.averageMatchLength();
        boolean betterDistance = context.shortDistanceShare(winnerLz) > context.shortDistanceShare(runnerLz);

        if (Math.abs(repDelta) < SMALL_REPETITION_DELTA && betterCost && betterLength && betterDistance) {
            return String.format(Locale.ROOT,
                    "Compared with %s, the global repetition evidence is close. Repetition metrics are broadly similar (%d vs %d repeated 32-byte substrings), but %s has lower estimated LZ token cost, longer average matches, and more short-distance matches, indicating a simpler and more stationary residual stream.",
                    comparison.getKey(), winnerRep32, runnerRep32, best);
        }
        if (Math.abs(repDelta) < SMALL_REPETITION_DELTA && (betterCost || betterLength || betterDistance)) {
            return String.format(Locale.ROOT,
                    "Compared with %s, the global repetition evidence is close. Repetition metrics are broadly similar (%d vs %d repeated 32-byte substrings), and match quality differs more than raw repetition count; %s shows the stronger local match structure overall.",
                    comparison.getKey(), winnerRep32, runnerRep32, best);
        }

        if (repDelta <= -LARGE_REPETITION_DELTA && betterCost) {
            return "Although " + comparison.getKey() + " shows substantially more repeated 32-byte substrings, " + best + " still achieves lower estimated LZ token cost, suggesting better match quality and token efficiency.";
        }

        String comparisonWording = MetricComparison.describe(winnerRep32, runnerRep32);
        if ("equal".equals(comparisonWording)) {
            if (betterCost) return best + " and " + comparison.getKey() + " have equal repeated 32-byte substring counts, while " + best + " has lower estimated LZ token cost.";
            return best + " wins over " + comparison.getKey() + " despite equal repeated 32-byte substring counts; raw repetition volume does not explain the result.";
        }
        if (comparisonWording.contains("same") || comparisonWording.contains("similar")) {
            return "Compared with " + comparison.getKey() + ", repetition metrics are " + comparisonWording + " (" + winnerRep32 + " vs " + runnerRep32
                    + " repeated 32-byte substrings); match quality is more informative than this tiny difference.";
        }
        return "Compared with " + comparison.getKey() + ", " + best + " produces " + comparisonWording + " repeated DEFLATE-friendly 32-byte substrings ("
                + winnerRep32 + " vs " + runnerRep32 + "), affecting global repeat structure.";
    }

    private String deflateSentence(String best, String localBest, FilteredStreamDiagnostics winner,
                                   ExplanationContext context,
                                   Map.Entry<String, FilteredStreamDiagnostics> comparison) {
        FilteredStreamDiagnostics runner = comparison == null ? null : comparison.getValue();
        boolean localConflict = !best.toUpperCase(Locale.ROOT).contains(localBest);

        if (runner == null) return DEFAULT_MESSAGE;

        LzParseDiagnostics winnerLz = winner.lzParseDiagnostics();
        LzParseDiagnostics runnerLz = runner.lzParseDiagnostics();
        int winnerRep32 = winner.repetitionMetrics().repeated32ByteSubstrings();
        int runnerRep32 = runner.repetitionMetrics().repeated32ByteSubstrings();
        double repDelta = relativeDelta(winnerRep32, runnerRep32);

        if (localConflict && "PAETH".equals(localBest) && repDelta >= LARGE_REPETITION_DELTA) {
            return best + " outperforms PAETH overall because stronger global repetition dominates local residual minimization for final DEFLATE size.";
        }

        if (localConflict && "PAETH".equals(localBest)
                && winnerLz.approximateLzCostBits() < runnerLz.approximateLzCostBits()) {
            return "Although PAETH slightly minimizes residual magnitude better, compared with " + comparison.getKey() + ", "
                    + best + " generates a more compression-friendly residual stream with better match lengths, nearer references, and lower estimated LZ token cost.";
        }

        if ("fixed-paeth".equals(best) || "paeth".equals(best)) {
            return "PAETH wins because predictor precision and global repetition are aligned in this stream.";
        }
        if ("fixed-sub".equals(best)) {
            return "fixed-sub benefits from scanline-local residual simplicity that translates into stable DEFLATE back-references.";
        }
        if ("fixed-up".equals(best)) {
            return "fixed-up benefits from vertically coherent residual simplicity that translates into stable DEFLATE back-references.";
        }
        return DEFAULT_MESSAGE;
    }

    private double relativeDelta(int winner, int runner) {
        if (runner <= 0) return winner > 0 ? 1.0 : 0.0;
        return (winner - runner) / (double) runner;
    }

    private String equivalenceSentence(ExplanationContext context) {
        return context.equivalentStrategies().stream().map(java.util.Map.Entry::getKey)
                .filter(name -> "genetic".equals(name) || context.best().equals("genetic"))
                .findFirst()
                .map(name -> {
                    String genetic = "genetic".equals(name) ? name : context.best();
                    String fixed = "genetic".equals(name) ? context.best() : name;
                    return "The " + genetic + " search converged to the same filter sequence as " + fixed
                            + ", independently rediscovering the identical filtered stream.";
                }).orElse("");
    }
}
