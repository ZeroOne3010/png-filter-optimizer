package io.github.zeroone3010.pngfilteropt.report;

import io.github.zeroone3010.pngfilteropt.diagnostics.FilteredStreamDiagnostics;

import java.util.Comparator;
import java.util.Map;

public final class CompressionExplanationGenerator {
    private static final String DEFAULT_MESSAGE = "Compression outcome reflects a close tradeoff between local predictor precision and global DEFLATE repetition.";

    public String explain(String best, Map<String, FilteredStreamDiagnostics> diagnostics) {
        FilteredStreamDiagnostics winner = diagnostics.get(best);
        if (winner == null) return DEFAULT_MESSAGE;

        String winnerName = best;
        String localBest = bestResidualFilter(diagnostics);

        String local = localSentence(localBest, winner);
        String global = globalSentence(winnerName, winner, diagnostics);
        String deflate = deflateSentence(winnerName, localBest, winner, diagnostics);

        return String.join(" ", local, global, deflate).trim();
    }

    private String localSentence(String localBest, FilteredStreamDiagnostics winner) {
        double ratio = winner.directionalSmoothness().verticalHorizontalRatio();
        if ("PAETH".equals(localBest)) {
            return "PAETH minimizes local residual magnitude." + directionalClause(ratio);
        }
        return localBest + " minimizes local residual magnitude." + directionalClause(ratio);
    }

    private String directionalClause(double ratio) {
        if (ratio < 0.85) return " Vertical smoothness is stronger than horizontal smoothness.";
        if (ratio > 1.20) return " Horizontal smoothness is stronger than vertical smoothness.";
        return " Directional smoothness is balanced across axes.";
    }

    private String globalSentence(String best, FilteredStreamDiagnostics winner, Map<String, FilteredStreamDiagnostics> diagnostics) {
        int winnerRep32 = winner.repetitionMetrics().repeated32ByteSubstrings();
        int runnerRep32 = diagnostics.entrySet().stream()
                .filter(e -> !e.getKey().equals(best))
                .map(e -> e.getValue().repetitionMetrics().repeated32ByteSubstrings())
                .max(Comparator.naturalOrder())
                .orElse(0);
        String intensity = intensityWord(winnerRep32, runnerRep32);

        if ("fixed-none".equals(best) && winner.rowsEqualToPrevious() > 0) {
            return "However, fixed-none preserves literal row structure with " + winner.rowsEqualToPrevious() + " repeated rows, reinforcing globally repeatable byte structure for DEFLATE.";
        }

        if (winnerRep32 > 0 || runnerRep32 > 0) {
            long winnerCost = winner.lzParseDiagnostics().approximateLzCostBits();
            long bestAltCost = diagnostics.entrySet().stream().filter(e -> !e.getKey().equals(best)).mapToLong(e -> e.getValue().lzParseDiagnostics().approximateLzCostBits()).min().orElse(winnerCost);
            if (winnerRep32 < runnerRep32 && winnerCost < bestAltCost) {
                return "Although alternatives have more repeated 32-byte substrings (" + winnerRep32 + " vs " + runnerRep32 + "), " + best + " has lower estimated LZ token cost and better practical match utility.";
            }
            String lzImpact = winnerRep32 >= runnerRep32
                    ? "creating stronger LZ-style match opportunities."
                    : "reducing LZ-style match opportunities relative to alternatives.";
            return "However, " + best + " produces " + intensity + " repeated DEFLATE-friendly 32-byte substrings (" + winnerRep32 + " vs " + runnerRep32 + "), " + lzImpact;
        }

        return "However, " + best + " wins on global stream structure rather than a single dominant local metric.";
    }

    private String deflateSentence(String best, String localBest, FilteredStreamDiagnostics winner, Map<String, FilteredStreamDiagnostics> diagnostics) {
        boolean localConflict = !best.toUpperCase().contains(localBest);
        int winnerLongest = winner.repetitionMetrics().longest32KiBMatch();
        int maxLongest = diagnostics.values().stream().mapToInt(v -> v.repetitionMetrics().longest32KiBMatch()).max().orElse(0);

        if (localConflict && "PAETH".equals(localBest)) {
            return best + " outperforms PAETH overall because global repetition outweighs local residual minimization for final DEFLATE size.";
        }
        if ("fixed-paeth".equals(best) || "paeth".equals(best)) {
            return "PAETH wins because predictor precision and global repetition are aligned in this stream.";
        }
        if (winnerLongest > 0 && winnerLongest == maxLongest) {
            return best + " also reaches the strongest long-range residual match depth, further improving compression-friendly repetition.";
        }
        if ("fixed-sub".equals(best)) {
            return "fixed-sub benefits from scanline-local residual structure that translates into stable DEFLATE back-references.";
        }
        if ("fixed-up".equals(best)) {
            return "fixed-up benefits from vertically coherent residual structure that translates into stable DEFLATE back-references.";
        }
        return DEFAULT_MESSAGE;
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
