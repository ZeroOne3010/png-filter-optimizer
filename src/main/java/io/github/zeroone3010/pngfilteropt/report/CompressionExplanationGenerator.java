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
        String localBest = bestResidualFilter(winner);

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
            return "However, " + best + " produces " + intensity + " repeated DEFLATE-friendly 32-byte substrings (" + winnerRep32 + " vs " + runnerRep32 + "), creating stronger LZ-style match opportunities.";
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
        if (winnerLongest > 0 && winnerLongest == maxLongest) {
            return best + " also reaches the strongest long-range residual match depth, further improving compression-friendly repetition.";
        }
        if ("fixed-sub".equals(best)) {
            return "fixed-sub benefits from scanline-local residual structure that translates into stable DEFLATE back-references.";
        }
        if ("fixed-up".equals(best)) {
            return "fixed-up benefits from vertically coherent residual structure that translates into stable DEFLATE back-references.";
        }
        if ("fixed-paeth".equals(best) || "paeth".equals(best)) {
            return "PAETH wins because predictor precision and global repetition are aligned in this stream.";
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
        return "slightly more";
    }

    private String bestResidualFilter(FilteredStreamDiagnostics winner) {
        var r = winner.residualDiagnostics();
        long min = Math.min(Math.min(Math.min(r.noneSumAbs(), r.subSumAbs()), Math.min(r.upSumAbs(), r.averageSumAbs())), r.paethSumAbs());
        if (min == r.noneSumAbs()) return "NONE";
        if (min == r.subSumAbs()) return "SUB";
        if (min == r.upSumAbs()) return "UP";
        if (min == r.averageSumAbs()) return "AVERAGE";
        return "PAETH";
    }
}
