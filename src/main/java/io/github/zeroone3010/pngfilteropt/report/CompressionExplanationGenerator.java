package io.github.zeroone3010.pngfilteropt.report;

import io.github.zeroone3010.pngfilteropt.diagnostics.FilteredStreamDiagnostics;
import io.github.zeroone3010.pngfilteropt.diagnostics.LzParseDiagnostics;

import java.util.Locale;
import java.util.Map;

public final class CompressionExplanationGenerator {
    private static final String DEFAULT_MESSAGE = "Compression outcome reflects a close tradeoff between local predictor precision and global DEFLATE repetition.";
    private static final double SMALL_REPETITION_DELTA = 0.05;
    private static final double LARGE_REPETITION_DELTA = 0.20;

    public String explain(String best, Map<String, FilteredStreamDiagnostics> diagnostics) {
        FilteredStreamDiagnostics winner = diagnostics.get(best);
        if (winner == null) return DEFAULT_MESSAGE;

        String localBest = bestResidualFilter(diagnostics);
        String local = localSentence(localBest, winner, best);
        String global = globalSentence(best, localBest, winner, diagnostics);
        String deflate = deflateSentence(best, localBest, winner, diagnostics);

        return String.join(" ", local, global, deflate).trim();
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

    private String globalSentence(String best, String localBest, FilteredStreamDiagnostics winner, Map<String, FilteredStreamDiagnostics> diagnostics) {
        if ("fixed-none".equals(best) && winner.rowsEqualToPrevious() > 0) {
            return "However, fixed-none preserves literal row structure with " + winner.rowsEqualToPrevious() + " repeated rows, reinforcing exact repeated byte runs for DEFLATE back-references.";
        }

        FilteredStreamDiagnostics runner = bestAlternative(best, diagnostics);
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
        boolean betterDistance = shortDistanceShare(winnerLz) > shortDistanceShare(runnerLz);

        if (Math.abs(repDelta) < SMALL_REPETITION_DELTA && betterCost && betterLength && betterDistance) {
            return String.format(Locale.ROOT,
                    "Repetition metrics are broadly similar (%d vs %d repeated 32-byte substrings), but %s has lower estimated LZ token cost, longer average matches, and more short-distance matches, indicating a simpler and more stationary residual stream.",
                    winnerRep32, runnerRep32, best);
        }
        if (Math.abs(repDelta) < SMALL_REPETITION_DELTA && (betterCost || betterLength || betterDistance)) {
            return String.format(Locale.ROOT,
                    "Repetition metrics are broadly similar (%d vs %d repeated 32-byte substrings), and match quality differs more than raw repetition count; %s shows the stronger local match structure overall.",
                    winnerRep32, runnerRep32, best);
        }

        if (repDelta <= -LARGE_REPETITION_DELTA && betterCost) {
            return "Although alternatives show substantially more repeated 32-byte substrings, " + best + " still achieves lower estimated LZ token cost, suggesting better match quality and token efficiency.";
        }

        return "However, " + best + " produces " + intensityWord(winnerRep32, runnerRep32) + " repeated DEFLATE-friendly 32-byte substrings ("
                + winnerRep32 + " vs " + runnerRep32 + "), strengthening global repeat structure.";
    }

    private String deflateSentence(String best, String localBest, FilteredStreamDiagnostics winner, Map<String, FilteredStreamDiagnostics> diagnostics) {
        FilteredStreamDiagnostics runner = bestAlternative(best, diagnostics);
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
            return "Although PAETH slightly minimizes residual magnitude better, " + best + " generates a more compression-friendly residual stream with better match lengths, nearer references, and lower estimated LZ token cost.";
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

    private double shortDistanceShare(LzParseDiagnostics lz) {
        long[] b = lz.matchDistanceBuckets();
        long total = 0;
        for (long v : b) total += v;
        if (total == 0) return 0;
        return b[0] / (double) total;
    }

    private FilteredStreamDiagnostics bestAlternative(String best, Map<String, FilteredStreamDiagnostics> diagnostics) {
        return diagnostics.entrySet().stream()
                .filter(e -> !e.getKey().equals(best))
                .min(Map.Entry.comparingByValue((a, b) -> Long.compare(a.lzParseDiagnostics().approximateLzCostBits(), b.lzParseDiagnostics().approximateLzCostBits())))
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    private double relativeDelta(int winner, int runner) {
        if (runner <= 0) return winner > 0 ? 1.0 : 0.0;
        return (winner - runner) / (double) runner;
    }

    private String intensityWord(int winner, int runner) {
        if (winner <= 0 && runner <= 0) return "comparable";
        if (runner <= 0) return "dramatically more";
        double ratio = (winner - runner) / (double) runner;
        if (ratio > 0.50) return "dramatically more";
        if (ratio > 0.20) return "substantially more";
        if (ratio < -0.20) return "significantly fewer";
        if (ratio < 0.0) return "slightly fewer";
        return "slightly more";
    }

    private String bestResidualFilter(Map<String, FilteredStreamDiagnostics> diagnostics) {
        String bestFilter = "PAETH";
        long bestScore = Long.MAX_VALUE;
        for (var entry : diagnostics.entrySet()) {
            long score = localResidualScore(entry.getKey(), entry.getValue());
            if (score < bestScore) {
                bestScore = score;
                bestFilter = filterLabel(entry.getKey(), entry.getValue());
            }
        }
        return bestFilter;
    }

    private long localResidualScore(String strategy, FilteredStreamDiagnostics diagnostics) {
        var r = diagnostics.residualDiagnostics();
        if (strategy.contains("none")) return r.noneSumAbs();
        if (strategy.contains("sub")) return r.subSumAbs();
        if (strategy.contains("up")) return r.upSumAbs();
        if (strategy.contains("average")) return r.averageSumAbs();
        if (strategy.contains("paeth")) return r.paethSumAbs();
        return Math.min(Math.min(Math.min(r.noneSumAbs(), r.subSumAbs()), Math.min(r.upSumAbs(), r.averageSumAbs())), r.paethSumAbs());
    }

    private String filterLabel(String strategy, FilteredStreamDiagnostics diagnostics) {
        if (strategy.contains("none")) return "NONE";
        if (strategy.contains("sub")) return "SUB";
        if (strategy.contains("up")) return "UP";
        if (strategy.contains("average")) return "AVERAGE";
        if (strategy.contains("paeth")) return "PAETH";
        var r = diagnostics.residualDiagnostics();
        long min = Math.min(Math.min(Math.min(r.noneSumAbs(), r.subSumAbs()), Math.min(r.upSumAbs(), r.averageSumAbs())), r.paethSumAbs());
        if (min == r.noneSumAbs()) return "NONE";
        if (min == r.subSumAbs()) return "SUB";
        if (min == r.upSumAbs()) return "UP";
        if (min == r.averageSumAbs()) return "AVERAGE";
        return "PAETH";
    }
}
