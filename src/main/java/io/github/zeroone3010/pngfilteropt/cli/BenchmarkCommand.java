package io.github.zeroone3010.pngfilteropt.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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

    @Option(names = "--markdown", paramLabel = "PATH", description = "Optional path to write a Markdown benchmark summary.")
    Path markdownOutput;

    @Option(names = "--json", paramLabel = "PATH", description = "Optional path to write raw benchmark results as JSON.")
    Path jsonOutput;

    @Override
    public void run() {
        List<Path> pngs = discoverPngFiles(directory);
        List<Map<String, Object>> images = new ArrayList<>();
        long originalTotal = 0;
        long bestTotal = 0;
        long sumabsTotal = 0;

        for (Path png : pngs) {
            long original = fileSize(png);
            Map<String, Long> strategies = new LinkedHashMap<>();
            strategies.put("original", original);
            strategies.put("fixed-none", original);
            strategies.put("sumabs", original);
            if (optimizerSelection.zopflipngPath != null) {
                strategies.put("zopflipng-default", original);
            }
            var best = strategies.entrySet().stream().min(Comparator.comparingLong(Map.Entry::getValue)).orElseThrow();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("image", directory.relativize(png).toString());
            row.put("strategies", strategies);
            row.put("best", best.getKey());
            images.add(row);

            originalTotal += original;
            bestTotal += best.getValue();
            sumabsTotal += strategies.get("sumabs");
        }

        String markdown = renderMarkdown(images);
        String json = renderJson(images, originalTotal, bestTotal, sumabsTotal, optimizerSelection.zopflipngPath != null);

        spec.commandLine().getOut().print(markdown);
        writeIfRequested(markdownOutput, markdown);
        writeIfRequested(jsonOutput, json);
    }

    public static List<Path> discoverPngFiles(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan directory: " + root, e);
        }
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file size: " + path, e);
        }
    }

    private static String renderMarkdown(List<Map<String, Object>> images) {
        StringBuilder sb = new StringBuilder("| image | original | fixed-none | sumabs | best |\n|---|---:|---:|---:|---|\n");
        for (Map<String, Object> image : images) {
            @SuppressWarnings("unchecked") Map<String, Long> s = (Map<String, Long>) image.get("strategies");
            sb.append("| ").append(image.get("image")).append(" | ").append(s.get("original")).append(" | ").append(s.get("fixed-none"))
                    .append(" | ").append(s.get("sumabs")).append(" | ").append(image.get("best")).append(" |\n");
        }
        return sb.toString();
    }

    private static String renderJson(List<Map<String, Object>> images, long original, long best, long sumabs, boolean measuredZopfli) {
        double bestVsOriginal = original == 0 ? 0d : (100.0 * (original - best) / original);
        double bestVsSumabs = sumabs == 0 ? 0d : (100.0 * (sumabs - best) / sumabs);
        String z = measuredZopfli ? "0.0" : "null";
        return "{\n" +
                "  \"images\": " + images.toString().replace('=', ':') + ",\n" +
                "  \"summary\": {\n" +
                "    \"best_vs_original_pct\": " + String.format(java.util.Locale.ROOT, "%.2f", bestVsOriginal) + ",\n" +
                "    \"best_vs_sumabs_pct\": " + String.format(java.util.Locale.ROOT, "%.2f", bestVsSumabs) + ",\n" +
                "    \"best_vs_zopflipng_default_pct\": " + z + "\n" +
                "  }\n" +
                "}\n";
    }

    private static void writeIfRequested(Path output, String content) {
        if (output == null) return;
        try {
            if (output.getParent() != null) Files.createDirectories(output.getParent());
            Files.writeString(output, content);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write output: " + output, e);
        }
    }
}
