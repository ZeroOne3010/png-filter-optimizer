package io.github.zeroone3010.pngfilteropt.cli;

import picocli.CommandLine.Option;

import java.nio.file.Path;

public final class CliOptions {
    public static final String DESC_BASELINE = "Preserves current filter strategy and only recompresses.";
    public static final String DESC_ENTROPY = "Chooses per-row filters by Shannon entropy heuristics.";
    public static final String DESC_ADAPTIVE = "Greedy per-row sum-of-absolute-values heuristic (fast, not true global DEFLATE optimization).";
    public static final String DESC_EXHAUSTIVE = "Beam search over row filter sequences scored by LZ-style heuristics; larger --beam explores more paths but is not globally optimal.";
    public static final String DESC_GENETIC = "Genetic 8-block filter search scored by fast DEFLATE-size heuristic.";
    public static final String DESC_FIXED_NONE = "Forces filter 0 (NONE) for every row as a fixed reference column.";
    public static final String DESC_FIXED_SUB = "Forces filter 1 (SUB) for every row as a fixed reference column.";
    public static final String DESC_FIXED_UP = "Forces filter 2 (UP) for every row as a fixed reference column.";
    public static final String DESC_FIXED_AVERAGE = "Forces filter 3 (AVERAGE) for every row as a fixed reference column.";
    public static final String DESC_FIXED_PAETH = "Forces filter 4 (PAETH) for every row as a fixed reference column.";
    public static final String DESC_ORIGINAL = "Original input PNG size without re-filtering.";
    public static final String DESC_ZOPFLIPNG_DEFAULT = "Original PNG size used as placeholder for default zopflipng recompression baseline.";
    public static final String DESC_BEST = "Smallest strategy value on each image row.";

    private CliOptions() {
    }

    public static class OptimizerSelection {
        @Option(
                names = "--optimizer",
                split = ",",
                paramLabel = "NAME[,NAME...]",
                description = {
                        "Optimizer(s) to run. Can be provided multiple times or as comma-separated values.",
                        "Available names:",
                        "  baseline  - " + DESC_BASELINE,
                        "  entropy   - " + DESC_ENTROPY,
                        "  adaptive  - " + DESC_ADAPTIVE,
                        "  exhaustive- " + DESC_EXHAUSTIVE,
                        "  genetic     - " + DESC_GENETIC,
                        "  fixed-none   - " + DESC_FIXED_NONE,
                        "  fixed-sub    - " + DESC_FIXED_SUB,
                        "  fixed-up     - " + DESC_FIXED_UP,
                        "  fixed-average- " + DESC_FIXED_AVERAGE,
                        "  fixed-paeth  - " + DESC_FIXED_PAETH
                }
        )
        public OptimizerName[] optimizers = {OptimizerName.ADAPTIVE};

        @Option(
                names = "--try-all",
                description = "Run every optimizer and pick the smallest output (ignores explicit --optimizer order)."
        )
        public boolean tryAll;

        @Option(
                names = "--zopflipng",
                paramLabel = "PATH",
                description = "Path to zopflipng executable. When provided, final recompression uses zopflipng."
        )
        public Path zopflipngPath;

        @Option(
                names = "--beam",
                defaultValue = "128",
                paramLabel = "WIDTH",
                description = "Beam width used by search-based optimizers (default: ${DEFAULT-VALUE})."
        )
        public int beamWidth;

        @Option(names = "--ga-blocks", defaultValue = "8", description = "Number of scanline blocks for the genetic optimizer.")
        public int gaBlocks;

        @Option(names = "--ga-population", defaultValue = "16", description = "Initial population size for the genetic optimizer.")
        public int gaPopulation;

        @Option(names = "--ga-survivors", defaultValue = "8", description = "Number of best genomes selected as parents.")
        public int gaSurvivors;

        @Option(names = "--ga-mutation-rate", defaultValue = "0.15", description = "Mutation probability in [0,1].")
        public double gaMutationRate;

        @Option(names = "--ga-runs", defaultValue = "1", description = "How many full GA runs to execute.")
        public int gaRuns;

        @Option(names = "--ga-seed", defaultValue = "12345", description = "Deterministic random seed for GA.")
        public long gaSeed;

        @Option(names = "--ga-time-limit-ms", defaultValue = "10000", description = "Time limit for GA optimization in milliseconds.")
        public long gaTimeLimitMs;

        @Option(names = "--optimizer-logs", description = "Print optimizer-specific insight/log lines when available.")
        public boolean optimizerLogs;
    }

    public enum OptimizerName {
        BASELINE,
        ENTROPY,
        ADAPTIVE,
        EXHAUSTIVE,
        GENETIC,
        FIXED_NONE,
        FIXED_SUB,
        FIXED_UP,
        FIXED_AVERAGE,
        FIXED_PAETH
    }
}
