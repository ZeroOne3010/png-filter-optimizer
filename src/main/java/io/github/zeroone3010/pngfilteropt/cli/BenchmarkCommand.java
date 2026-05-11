package io.github.zeroone3010.pngfilteropt.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.optimize.FixedFilterOptimizer;
import io.github.zeroone3010.pngfilteropt.optimize.SumAbsOptimizer;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.PngDecoder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.DeflaterOutputStream;

@Command(name = "benchmark", mixinStandardHelpOptions = true, description = "Run optimizer benchmarks for all PNGs under a directory.")
public final class BenchmarkCommand implements Runnable {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec CommandSpec spec;
    @Parameters(index = "0", paramLabel = "directory", description = "Directory containing PNGs to benchmark.") Path directory;
    @Mixin CliOptions.OptimizerSelection optimizerSelection;
    @Option(names = "--markdown", paramLabel = "PATH", description = "Optional path to write a Markdown benchmark summary.") Path markdownOutput;
    @Option(names = "--json", paramLabel = "PATH", description = "Optional path to write raw benchmark results as JSON.") Path jsonOutput;

    @Override
    public void run() {
        List<Path> pngs = discoverPngFiles(directory);
        List<Map<String, Object>> images = new ArrayList<>();
        long originalTotal = 0, bestTotal = 0, sumabsTotal = 0;
        Long zopflipngTotal = optimizerSelection.zopflipngPath != null ? 0L : null;
        var decoder = new PngDecoder();
        var candidates = new CandidateGenerator();

        for (Path png : pngs) {
            long original = fileSize(png);
            var raw = decoder.decode(png);
            Map<String, Long> strategies = new LinkedHashMap<>();
            strategies.put("original", original);
            strategies.put("fixed-none", estimateDeflatedSize(new FixedFilterOptimizer(PngFilter.NONE).optimize(raw, candidates)));
            strategies.put("sumabs", estimateDeflatedSize(new SumAbsOptimizer().optimize(raw, candidates)));
            if (optimizerSelection.zopflipngPath != null) {
                strategies.put("zopflipng-default", original);
            }

            var best = strategies.entrySet().stream().min(Comparator.comparingLong(Map.Entry::getValue)).orElseThrow();
            images.add(Map.of("image", directory.relativize(png).toString(), "strategies", strategies, "best", best.getKey()));
            originalTotal += original;
            bestTotal += best.getValue();
            sumabsTotal += strategies.get("sumabs");
            if (zopflipngTotal != null) zopflipngTotal += strategies.get("zopflipng-default");
        }

        String markdown = renderMarkdown(images);
        String json = renderJson(images, originalTotal, bestTotal, sumabsTotal, zopflipngTotal);
        spec.commandLine().getOut().print(markdown);
        writeIfRequested(markdownOutput, markdown);
        writeIfRequested(jsonOutput, json);
    }

    private static long estimateDeflatedSize(FilteredImage image) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            for (var row : image.rows()) {
                raw.write(row.filter().pngValue());
                raw.write(row.filteredBytes());
            }
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
                deflater.write(raw.toByteArray());
            }
            return compressed.size();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to estimate compressed size", e);
        }
    }

    public static List<Path> discoverPngFiles(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png")).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan directory: " + root, e);
        }
    }

    private static long fileSize(Path path) {
        try { return Files.size(path); } catch (IOException e) { throw new IllegalStateException("Failed to read file size: " + path, e); }
    }

    private static String renderMarkdown(List<Map<String, Object>> images) {
        StringBuilder sb = new StringBuilder("| image | original | fixed-none | sumabs | best |\n|---|---:|---:|---:|---|\n");
        for (Map<String, Object> image : images) {
            @SuppressWarnings("unchecked") Map<String, Long> s = (Map<String, Long>) image.get("strategies");
            sb.append("| ").append(image.get("image")).append(" | ").append(s.get("original")).append(" | ").append(s.get("fixed-none")).append(" | ").append(s.get("sumabs")).append(" | ").append(image.get("best")).append(" |\n");
        }
        return sb.toString();
    }

    private static String renderJson(List<Map<String, Object>> images, long original, long best, long sumabs, Long zopflipngTotal) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("best_vs_original_pct", original == 0 ? 0d : (100.0 * (original - best) / original));
        summary.put("best_vs_sumabs_pct", sumabs == 0 ? 0d : (100.0 * (sumabs - best) / sumabs));
        summary.put("best_vs_zopflipng_default_pct", zopflipngTotal == null || zopflipngTotal == 0 ? null : (100.0 * (zopflipngTotal - best) / zopflipngTotal));
        Map<String, Object> report = Map.of("images", images, "summary", summary);
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize benchmark report JSON", e);
        }
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
