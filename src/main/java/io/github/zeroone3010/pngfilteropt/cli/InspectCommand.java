package io.github.zeroone3010.pngfilteropt.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;

@Command(
        name = "inspect",
        mixinStandardHelpOptions = true,
        description = "Inspect PNG filter usage and metadata without rewriting the image."
)
public final class InspectCommand implements Runnable {

    @Parameters(index = "0", paramLabel = "input.png", description = "Input PNG to inspect.")
    Path input;

    @Override
    public void run() {
        new CommandLine(this).getOut().printf("Parsed inspect request: input=%s%n", input);
    }
}
