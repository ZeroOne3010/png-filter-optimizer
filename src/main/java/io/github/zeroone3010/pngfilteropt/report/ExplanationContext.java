package io.github.zeroone3010.pngfilteropt.report;

import io.github.zeroone3010.pngfilteropt.diagnostics.FilteredStreamDiagnostics;
import io.github.zeroone3010.pngfilteropt.diagnostics.LzParseDiagnostics;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record ExplanationContext(String best, Map<String, FilteredStreamDiagnostics> diagnostics, Map<String, Long> finalSizes) {
    public ExplanationContext(String best, Map<String, FilteredStreamDiagnostics> diagnostics) {
        this(best, diagnostics, Map.of());
    }
    public java.util.List<MetricObservation> metricObservations() {
        FilteredStreamDiagnostics winner = winner();
        if (winner == null) return java.util.List.of();
        java.util.ArrayList<MetricObservation> observations = new java.util.ArrayList<>();
        observations.add(new MetricObservation("local residual", localBestFilter() + " has the strongest local residual sumAbs signal.", 0.75));
        runnerUpByLzCost().ifPresent(runner -> {
            long winnerCost = winner.lzParseDiagnostics().approximateLzCostBits();
            long runnerCost = runner.getValue().lzParseDiagnostics().approximateLzCostBits();
            double strength = runnerCost <= 0 ? 0.0 : Math.min(1.0, Math.abs(runnerCost - winnerCost) / (double) runnerCost);
            observations.add(new MetricObservation("estimated LZ token cost", best + " costs " + winnerCost + " bits versus " + runner.getKey() + " at " + runnerCost + " bits.", strength));
            observations.add(new MetricObservation("repeated 32-byte substrings", best + " has " + winner.repetitionMetrics().repeated32ByteSubstrings() + " repeated 32-byte substrings versus " + runner.getValue().repetitionMetrics().repeated32ByteSubstrings() + ".", Math.abs(repeated32DeltaVsRunner())));
        });
        observations.add(new MetricObservation("filter distribution", "The winner is dominated by " + dominantWinnerFilter() + " at " + String.format(Locale.ROOT, "%.1f", dominantWinnerShare()) + "% of rows.", dominantWinnerShare() / 100.0));
        return java.util.List.copyOf(observations);
    }

    public FilteredStreamDiagnostics winner() {
        return diagnostics.get(best);
    }

    public Optional<Map.Entry<String, FilteredStreamDiagnostics>> runnerUpByLzCost() {
        return diagnostics.entrySet().stream()
                .filter(this::isDistinctCandidate)
                .min(Comparator.comparingLong(e -> e.getValue().lzParseDiagnostics().approximateLzCostBits()));
    }

    public Optional<Map.Entry<String, FilteredStreamDiagnostics>> finalSizeRunnerUp() {
        if (finalSizes.isEmpty()) return runnerUpByLzCost();
        return diagnostics.entrySet().stream()
                .filter(this::isDistinctCandidate)
                .filter(e -> finalSizes.containsKey(e.getKey()))
                .min(Comparator.comparingLong(e -> finalSizes.get(e.getKey())));
    }

    public Optional<Map.Entry<String, FilteredStreamDiagnostics>> bestFixedStrategy() {
        return diagnostics.entrySet().stream().filter(this::isDistinctCandidate)
                .filter(e -> e.getKey().startsWith("fixed-"))
                .min(Comparator.comparingLong(e -> finalSizes.getOrDefault(e.getKey(),
                        e.getValue().lzParseDiagnostics().approximateLzCostBits())));
    }

    public java.util.List<Map.Entry<String, FilteredStreamDiagnostics>> equivalentStrategies() {
        FilteredStreamDiagnostics winner = winner();
        if (winner == null) return java.util.List.of();
        return diagnostics.entrySet().stream().filter(e -> !e.getKey().equals(best))
                .filter(e -> winner.isFilterEquivalentTo(e.getValue())).toList();
    }

    private boolean isDistinctCandidate(Map.Entry<String, FilteredStreamDiagnostics> entry) {
        if (entry.getKey().equals(best) || winner() == null || winner().isFilterEquivalentTo(entry.getValue())) return false;
        FilteredStreamDiagnostics candidate = entry.getValue();
        return winner().lzParseDiagnostics().approximateLzCostBits() != candidate.lzParseDiagnostics().approximateLzCostBits()
                || Double.compare(winner().lzParseDiagnostics().averageMatchLength(), candidate.lzParseDiagnostics().averageMatchLength()) != 0
                || winner().repetitionMetrics().repeated32ByteSubstrings() != candidate.repetitionMetrics().repeated32ByteSubstrings()
                || !winner().filterUsage().counts().equals(candidate.filterUsage().counts());
    }

    public String localBestFilter() {
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

    public boolean bestUsesFilter(String filter) {
        return best.toUpperCase(Locale.ROOT).contains(filter);
    }

    public PngFilter dominantWinnerFilter() {
        FilteredStreamDiagnostics winner = winner();
        if (winner == null) return PngFilter.PAETH;
        return winner.filterUsage().counts().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(PngFilter.PAETH);
    }

    public double dominantWinnerShare() {
        FilteredStreamDiagnostics winner = winner();
        if (winner == null) return 0;
        int total = totalRows(winner);
        if (total == 0) return fixedStrategyFilter().isPresent() ? 100.0 : 0.0;
        return winner.filterUsage().percentage(dominantWinnerFilter());
    }

    public int distinctWinnerFilters() {
        FilteredStreamDiagnostics winner = winner();
        if (winner == null) return 0;
        return (int) winner.filterUsage().counts().values().stream().filter(v -> v > 0).count();
    }

    public Optional<PngFilter> fixedStrategyFilter() {
        if (best.contains("fixed-none")) return Optional.of(PngFilter.NONE);
        if (best.contains("fixed-sub")) return Optional.of(PngFilter.SUB);
        if (best.contains("fixed-up")) return Optional.of(PngFilter.UP);
        if (best.contains("fixed-average")) return Optional.of(PngFilter.AVERAGE);
        if (best.contains("fixed-paeth")) return Optional.of(PngFilter.PAETH);
        return Optional.empty();
    }

    public double winnerVsRunnerCostImprovement() {
        var runner = runnerUpByLzCost().map(Map.Entry::getValue).orElse(null);
        if (winner() == null || runner == null) return 0;
        long runnerCost = runner.lzParseDiagnostics().approximateLzCostBits();
        if (runnerCost <= 0) return 0;
        return (runnerCost - winner().lzParseDiagnostics().approximateLzCostBits()) / (double) runnerCost;
    }

    public double repeated32DeltaVsRunner() {
        var runner = runnerUpByLzCost().map(Map.Entry::getValue).orElse(null);
        if (winner() == null || runner == null) return 0;
        int winnerRep = winner().repetitionMetrics().repeated32ByteSubstrings();
        int runnerRep = runner.repetitionMetrics().repeated32ByteSubstrings();
        if (runnerRep <= 0) return winnerRep > 0 ? 1.0 : 0.0;
        return (winnerRep - runnerRep) / (double) runnerRep;
    }

    public double shortDistanceShare(LzParseDiagnostics lz) {
        long[] b = lz.matchDistanceBuckets();
        long total = 0;
        for (long v : b) total += v;
        if (total == 0) return 0;
        return b[0] / (double) total;
    }

    private int totalRows(FilteredStreamDiagnostics diagnostics) {
        int totalRows = diagnostics.filterUsage().totalRows();
        if (totalRows > 0) return totalRows;
        return diagnostics.filterUsage().counts().values().stream().mapToInt(Integer::intValue).sum();
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
