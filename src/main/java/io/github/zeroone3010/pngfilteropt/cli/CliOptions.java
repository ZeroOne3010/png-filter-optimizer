package io.github.zeroone3010.pngfilteropt.cli;

import picocli.CommandLine.Option;

import java.nio.file.Path;

public final class CliOptions {
    public static final String DESC_BASELINE = "Preserves current filter strategy and only recompresses.";
    public static final String DESC_ENTROPY = "Chooses per-row filters by Shannon entropy heuristics.";
    public static final String DESC_ADAPTIVE = "Uses dynamic programming to optimize global compressed size.";
    public static final String DESC_EXHAUSTIVE = "Uses beam search over row-filter candidates with width controlled by --beam.";
    public static final String DESC_LITERAL = "Forces filter 0 (NONE) for every row.";
    public static final String DESC_FIXED_NONE = "Forces filter 0 (NONE) for every row as a fixed reference column.";
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
                        "  literal   - " + DESC_LITERAL
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
    }

    public enum OptimizerName {
        BASELINE,
        ENTROPY,
        ADAPTIVE,
        EXHAUSTIVE,
        LITERAL
    }
}
