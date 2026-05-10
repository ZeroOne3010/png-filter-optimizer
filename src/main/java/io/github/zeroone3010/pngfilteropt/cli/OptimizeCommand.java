package io.github.zeroone3010.pngfilteropt.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

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

    @Parameters(index = "0", paramLabel = "input.png", description = "Input PNG to optimize.")
    Path input;

    @Parameters(index = "1", paramLabel = "output.png", description = "Destination file for optimized PNG.")
    Path output;

    @Mixin
    CliOptions.OptimizerSelection optimizerSelection;

    @Override
    public void run() {
        CommandLine commandLine = new CommandLine(this);
        commandLine.getOut().printf(
                "Parsed optimize request: input=%s, output=%s, optimizers=%s, tryAll=%s, zopflipng=%s, beam=%d%n",
                input,
                output,
                java.util.Arrays.toString(optimizerSelection.optimizers),
                optimizerSelection.tryAll,
                optimizerSelection.zopflipngPath,
                optimizerSelection.beamWidth
        );
    }
}
