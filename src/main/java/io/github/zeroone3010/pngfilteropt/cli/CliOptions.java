package io.github.zeroone3010.pngfilteropt.cli;

import picocli.CommandLine.Option;

import java.nio.file.Path;

public final class CliOptions {

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
                        "  baseline  - Preserves current filter strategy and only recompresses.",
                        "  entropy   - Chooses per-row filters by Shannon entropy heuristics.",
                        "  adaptive  - Uses dynamic programming to optimize global compressed size.",
                        "  exhaustive- Evaluates all filter sequences up to --beam width.",
                        "  literal   - Forces filter 0 (NONE) for every row."
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
