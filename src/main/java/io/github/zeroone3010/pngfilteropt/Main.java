package io.github.zeroone3010.pngfilteropt;

import io.github.zeroone3010.pngfilteropt.cli.BenchmarkCommand;
import io.github.zeroone3010.pngfilteropt.cli.InspectCommand;
import io.github.zeroone3010.pngfilteropt.cli.OptimizeCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "pngfilteropt",
        mixinStandardHelpOptions = true,
        description = "PNG scanline filter optimizer toolkit.",
        subcommands = {
                OptimizeCommand.class,
                InspectCommand.class,
                BenchmarkCommand.class
        }
)
public final class Main implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).setCaseInsensitiveEnumValuesAllowed(true).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
