package io.github.zeroone3010.pngfilteropt.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Path;

@Command(
        name = "benchmark",
        mixinStandardHelpOptions = true,
        description = "Run optimizer benchmarks for all PNGs under a directory."
)
public final class BenchmarkCommand implements Runnable {

    @Spec
    CommandSpec spec;

    @Parameters(index = "0", paramLabel = "directory", description = "Directory containing PNGs to benchmark.")
    Path directory;

    @Mixin
    CliOptions.OptimizerSelection optimizerSelection;

    @Option(
            names = "--markdown",
            paramLabel = "PATH",
            description = "Optional path to write a Markdown benchmark summary."
    )
    Path markdownOutput;

    @Option(
            names = "--json",
            paramLabel = "PATH",
            description = "Optional path to write raw benchmark results as JSON."
    )
    Path jsonOutput;

    @Override
    public void run() {
        spec.commandLine().getOut().printf(
                "Parsed benchmark request: directory=%s, optimizers=%s, tryAll=%s, zopflipng=%s, beam=%d, markdown=%s, json=%s%n",
                directory,
                java.util.Arrays.toString(optimizerSelection.optimizers),
                optimizerSelection.tryAll,
                optimizerSelection.zopflipngPath,
                optimizerSelection.beamWidth,
                markdownOutput,
                jsonOutput
        );
    }
}
