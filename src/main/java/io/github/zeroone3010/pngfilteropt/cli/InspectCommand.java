package io.github.zeroone3010.pngfilteropt.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Path;

@Command(
        name = "inspect",
        mixinStandardHelpOptions = true,
        description = "Inspect PNG filter usage and metadata without rewriting the image."
)
public final class InspectCommand implements Runnable {

    @Spec
    CommandSpec spec;

    @Parameters(index = "0", paramLabel = "input.png", description = "Input PNG to inspect.")
    Path input;

    @Override
    public void run() {
        spec.commandLine().getOut().printf("Parsed inspect request: input=%s%n", input);
    }
}
