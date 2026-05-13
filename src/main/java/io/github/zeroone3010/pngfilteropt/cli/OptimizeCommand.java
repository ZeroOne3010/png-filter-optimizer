package io.github.zeroone3010.pngfilteropt.cli;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.optimize.EntropyOptimizer;
import io.github.zeroone3010.pngfilteropt.optimize.FilterOptimizer;
import io.github.zeroone3010.pngfilteropt.optimize.LzBeamOptimizer;
import io.github.zeroone3010.pngfilteropt.optimize.SumAbsOptimizer;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.FilterInspector;
import io.github.zeroone3010.pngfilteropt.png.FilteredRow;
import io.github.zeroone3010.pngfilteropt.png.PngDecoder;
import io.github.zeroone3010.pngfilteropt.png.PngEncoder;
import io.github.zeroone3010.pngfilteropt.zopfli.ZopfliRunner;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Command(
        name = "optimize",
        mixinStandardHelpOptions = true,
        description = "Optimize PNG filtering and write optimized image to output path.",
        synopsisHeading = "Usage: ",
        synopsisSubcommandLabel = "COMMAND",
        descriptionHeading = "%nDescription:%n",
        optionListHeading = "%nOptions:%n",
        parameterListHeading = "%nParameters:%n"
)
public final class OptimizeCommand implements Runnable {

    @Spec
    CommandSpec spec;

    @Parameters(index = "0", paramLabel = "input.png", description = "Input PNG to optimize.")
    Path input;

    @Parameters(index = "1", paramLabel = "output.png", description = "Destination file for optimized PNG.")
    Path output;

    @Mixin
    CliOptions.OptimizerSelection optimizerSelection;

    @Override
    public void run() {
        var decoder = new PngDecoder();
        var encoder = new PngEncoder();
        var candidates = new CandidateGenerator();
        var inspector = new FilterInspector();

        var raw = decoder.decode(input);
        List<CliOptions.OptimizerName> selected = optimizerSelection.tryAll
                ? List.of(CliOptions.OptimizerName.values())
                : Arrays.asList(optimizerSelection.optimizers);

        Map<CliOptions.OptimizerName, FilterOptimizer> optimizers = Map.of(
                CliOptions.OptimizerName.ENTROPY, new EntropyOptimizer(),
                CliOptions.OptimizerName.ADAPTIVE, new SumAbsOptimizer(),
                CliOptions.OptimizerName.EXHAUSTIVE, new LzBeamOptimizer()
        );

        FilteredImage best = null;
        String strategy = null;
        long bestSize = Long.MAX_VALUE;
        Path primaryOutput = output;

        for (CliOptions.OptimizerName name : selected) {
            FilteredImage candidate;
            if (name == CliOptions.OptimizerName.BASELINE) {
                var inputFilters = inspector.listFilters(input, raw);
                List<FilteredRow> rows = new java.util.ArrayList<>(raw.height());
                for (int y = 0; y < raw.height(); y++) {
                    PngFilter originalFilter = inputFilters.get(y);
                    var row = candidates.generateCandidates(raw, y).stream()
                            .filter(c -> c.filter() == originalFilter)
                            .findFirst()
                            .orElseThrow();
                    rows.add(row);
                }
                candidate = new FilteredImage(raw, rows);
            } else {
                FilterOptimizer optimizer = optimizers.get(name);
                candidate = optimizer.optimize(raw, candidates);
            }
            encoder.encode(candidate, primaryOutput);
            long size;
            try {
                size = java.nio.file.Files.size(primaryOutput);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Failed to read output size", e);
            }
            if (size < bestSize) {
                best = candidate;
                bestSize = size;
                strategy = name.name().toLowerCase();
            }
        }

        encoder.encode(best, output);
        Long zopfliSize = null;
        if (optimizerSelection.zopflipngPath != null) {
            Path zopfliOutput = output.resolveSibling(output.getFileName() + ".zopfli.png");
            zopfliSize = new ZopfliRunner().recompress(output, zopfliOutput, optimizerSelection.zopflipngPath);
            if (zopfliSize < bestSize) {
                try {
                    java.nio.file.Files.move(zopfliOutput, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("Failed to keep zopflipng output", e);
                }
                bestSize = zopfliSize;
                strategy = strategy + "+zopfli";
            } else {
                try {
                    java.nio.file.Files.deleteIfExists(zopfliOutput);
                } catch (java.io.IOException ignored) {
                }
            }
        }

        spec.commandLine().getOut().printf(
                "optimize strategy=%s output_bytes=%d%s%n",
                strategy,
                bestSize,
                zopfliSize == null ? "" : " zopfli_bytes=" + zopfliSize
        );
    }
}
