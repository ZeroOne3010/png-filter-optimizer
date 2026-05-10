package io.github.zeroone3010.pngfilteropt.cli;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.optimize.FixedFilterOptimizer;
import io.github.zeroone3010.pngfilteropt.png.FilterInspector;
import io.github.zeroone3010.pngfilteropt.png.PngDecoder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

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
        var image = new PngDecoder().decode(input);
        var filtered = new FixedFilterOptimizer(PngFilter.NONE).optimize(image, new CandidateGenerator());
        var counts = new FilterInspector().countFilters(filtered);
        spec.commandLine().getOut().printf("Image %s: %dx%d bpp=%d filters=%s%n", input, image.width(), image.height(), image.bytesPerPixel(), counts);
    }
}
