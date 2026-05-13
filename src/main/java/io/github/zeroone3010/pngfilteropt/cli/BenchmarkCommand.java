package io.github.zeroone3010.pngfilteropt.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.optimize.EntropyOptimizer;
import io.github.zeroone3010.pngfilteropt.optimize.FilterOptimizer;
import io.github.zeroone3010.pngfilteropt.optimize.FixedFilterOptimizer;
import io.github.zeroone3010.pngfilteropt.optimize.LzBeamOptimizer;
import io.github.zeroone3010.pngfilteropt.optimize.SumAbsOptimizer;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.FilterInspector;
import io.github.zeroone3010.pngfilteropt.png.FilteredRow;
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
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.DeflaterOutputStream;

@Command(name = "benchmark", mixinStandardHelpOptions = true, description = "Run optimizer benchmarks for all PNGs under a directory.")
public final class BenchmarkCommand implements Runnable {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Spec CommandSpec spec;
    @Parameters(index = "0", arity = "0..1", paramLabel = "directory", defaultValue = "src/test/resources/test-images",
            description = "Directory containing PNGs to benchmark.") Path directory;
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
        var inspector = new FilterInspector();
        var selected = optimizerSelection.tryAll ? List.of(CliOptions.OptimizerName.values()) : Arrays.asList(optimizerSelection.optimizers);
        spec.commandLine().getOut().printf(
                "benchmark_columns: selected=[%s]; always-on=[original, fixed-none%s]%n",
                selected.stream().map(name -> name.name().toLowerCase().replace('_', '-')).collect(Collectors.joining(", ")),
                optimizerSelection.zopflipngPath != null ? ", zopflipng-default" : ""
        );
        Map<CliOptions.OptimizerName, FilterOptimizer> optimizers = Map.of(
                CliOptions.OptimizerName.ENTROPY, new EntropyOptimizer(),
                CliOptions.OptimizerName.ADAPTIVE, new SumAbsOptimizer(),
                CliOptions.OptimizerName.EXHAUSTIVE, new LzBeamOptimizer(optimizerSelection.beamWidth),
                CliOptions.OptimizerName.FIXED_NONE, new FixedFilterOptimizer(PngFilter.NONE)
        );

        for (Path png : pngs) {
            long original = fileSize(png);
            var raw = decoder.decode(png);
            Map<String, Long> strategies = new LinkedHashMap<>();
            strategies.put("original", original);

            for (CliOptions.OptimizerName name : selected) {
                FilteredImage optimized;
                String key = name.name().toLowerCase().replace('_', '-');
                if (name == CliOptions.OptimizerName.BASELINE) {
                    var inputFilters = inspector.listFilters(png, raw);
                    List<FilteredRow> rows = new ArrayList<>(raw.height());
                    for (int y = 0; y < raw.height(); y++) {
                        PngFilter originalFilter = inputFilters.get(y);
                        var row = candidates.generateCandidates(raw, y).stream().filter(c -> c.filter() == originalFilter).findFirst().orElseThrow();
                        rows.add(row);
                    }
                    optimized = new FilteredImage(raw, rows);
                } else {
                    optimized = optimizers.get(name).optimize(raw, candidates);
                }
                strategies.put(key, estimateDeflatedSize(optimized));
            }

            strategies.put("fixed-none", estimateDeflatedSize(new FixedFilterOptimizer(PngFilter.NONE).optimize(raw, candidates)));
            if (optimizerSelection.zopflipngPath != null) {
                strategies.put("zopflipng-default", original);
            }

            var best = strategies.entrySet().stream().min(Comparator.comparingLong(Map.Entry::getValue)).orElseThrow();
            images.add(Map.of("image", directory.relativize(png).toString(), "strategies", strategies, "best", best.getKey()));
            originalTotal += original;
            bestTotal += best.getValue();
            long adaptiveBaseline = estimateDeflatedSize(new SumAbsOptimizer().optimize(raw, candidates));
            sumabsTotal += adaptiveBaseline;
            if (zopflipngTotal != null) zopflipngTotal += strategies.get("zopflipng-default");
        }

        String markdown = renderMarkdown(images);
        String json = renderJson(images, originalTotal, bestTotal, sumabsTotal, zopflipngTotal);
        spec.commandLine().getOut().print(markdown);
        spec.commandLine().getOut().flush();
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
        if (images.isEmpty()) {
            return "| image | original | best |\n|---|---:|---|\n\n### Legend\n- `original`: " + CliOptions.DESC_ORIGINAL + "\n- `best`: " + CliOptions.DESC_BEST + "\n";
        }
        @SuppressWarnings("unchecked") Map<String, Long> first = (Map<String, Long>) images.get(0).get("strategies");
        List<String> columns = new ArrayList<>(first.keySet());
        columns.remove("original");
        StringBuilder sb = new StringBuilder("| image | original");
        for (String column : columns) sb.append(" | ").append(column);
        sb.append(" | best |\n|---|---:");
        for (int i = 0; i < columns.size(); i++) sb.append("|---:");
        sb.append("|---|\n");
        for (Map<String, Object> image : images) {
            @SuppressWarnings("unchecked") Map<String, Long> s = (Map<String, Long>) image.get("strategies");
            sb.append("| ").append(image.get("image")).append(" | ").append(s.get("original"));
            for (String column : columns) sb.append(" | ").append(s.get(column));
            sb.append(" | ").append(image.get("best")).append(" |\n");
        }
        sb.append("\n### Legend\n");
        List<String> present = new ArrayList<>();
        present.add("original");
        present.addAll(columns);
        present.add("best");
        for (String key : present) {
            sb.append("- `").append(key).append("`: ").append(legendText(key)).append("\n");
        }
        return sb.toString();
    }

    private static String legendText(String key) {
        return switch (key) {
            case "original" -> CliOptions.DESC_ORIGINAL;
            case "baseline" -> CliOptions.DESC_BASELINE;
            case "entropy" -> CliOptions.DESC_ENTROPY;
            case "adaptive" -> CliOptions.DESC_ADAPTIVE;
            case "exhaustive" -> CliOptions.DESC_EXHAUSTIVE;
            case "fixed-none" -> CliOptions.DESC_FIXED_NONE;
            case "zopflipng-default" -> CliOptions.DESC_ZOPFLIPNG_DEFAULT;
            case "best" -> CliOptions.DESC_BEST;
            default -> "Strategy column.";
        };
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
