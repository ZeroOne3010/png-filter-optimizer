package io.github.zeroone3010.pngfilteropt.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.zeroone3010.pngfilteropt.diagnostics.DiagnosticsCalculator;
import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.optimize.EntropyOptimizer;
import io.github.zeroone3010.pngfilteropt.optimize.FilterOptimizer;
import io.github.zeroone3010.pngfilteropt.optimize.FixedFilterOptimizer;
import io.github.zeroone3010.pngfilteropt.optimize.LzBeamOptimizer;
import io.github.zeroone3010.pngfilteropt.optimize.GeneticSplitOptimizer;
import io.github.zeroone3010.pngfilteropt.optimize.SumAbsOptimizer;
import io.github.zeroone3010.pngfilteropt.png.*;
import io.github.zeroone3010.pngfilteropt.report.MarkdownDiagnosticsRenderer;
import io.github.zeroone3010.pngfilteropt.zopfli.ZopfliRunner;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.CommandSpec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.DeflaterOutputStream;

@Command(name = "benchmark", mixinStandardHelpOptions = true, description = "Run optimizer benchmarks for all PNGs under a directory.")
public final class BenchmarkCommand implements Runnable {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Spec CommandSpec spec;
    @Parameters(index = "0", arity = "0..1", defaultValue = "src/test/resources/test-images") Path directory;
    @Option(names = "--file", description = "Benchmark only a single PNG file (absolute path or path relative to benchmark directory).") Path singleFile;
    @Mixin CliOptions.OptimizerSelection optimizerSelection;
    @Option(names = "--markdown") Path markdownOutput;
    @Option(names = "--json") Path jsonOutput;

    @Override public void run() {
        var decoder = new PngDecoder(); var encoder = new PngEncoder(); var inspector = new FilterInspector(); var candidates = new CandidateGenerator();
        var selected = optimizerSelection.tryAll ? List.of(CliOptions.OptimizerName.values()) : Arrays.asList(optimizerSelection.optimizers);
        var optimizers = Map.of(
                CliOptions.OptimizerName.ENTROPY, new EntropyOptimizer(), CliOptions.OptimizerName.ADAPTIVE, new SumAbsOptimizer(),
                CliOptions.OptimizerName.EXHAUSTIVE, new LzBeamOptimizer(optimizerSelection.beamWidth), CliOptions.OptimizerName.GENETIC, new GeneticSplitOptimizer(optimizerSelection.gaBlocks, optimizerSelection.gaEvaluations, optimizerSelection.gaPopulation, optimizerSelection.gaSurvivors, optimizerSelection.gaEliteCount, optimizerSelection.gaGenerations, optimizerSelection.gaMutationRate, optimizerSelection.gaSeed, optimizerSelection.gaInitialMaxFilters, optimizerSelection.gaPrescreen, optimizerSelection.gaPrescreenFactor), CliOptions.OptimizerName.FIXED_NONE, new FixedFilterOptimizer(PngFilter.NONE),
                CliOptions.OptimizerName.FIXED_SUB, new FixedFilterOptimizer(PngFilter.SUB), CliOptions.OptimizerName.FIXED_UP, new FixedFilterOptimizer(PngFilter.UP),
                CliOptions.OptimizerName.FIXED_AVERAGE, new FixedFilterOptimizer(PngFilter.AVERAGE), CliOptions.OptimizerName.FIXED_PAETH, new FixedFilterOptimizer(PngFilter.PAETH));

        List<Map<String, Object>> images = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        var diagnosticsCalculator = new DiagnosticsCalculator();
        for (Path png : benchmarkTargets()) {
            RawImage raw;
            try {
                raw = decoder.decode(png);
            } catch (RuntimeException e) {
                String reason = detectNonPngHint(png);
                String imageLabel = imageLabel(png);
                skipped.add(imageLabel + " :: " + e.getClass().getSimpleName() + (reason.isBlank() ? "" : " (" + reason + ")"));
                spec.commandLine().getErr().printf(
                        "[WARN] Skipping unreadable PNG: %s%n        reason: %s%s%n",
                        png,
                        e.getMessage(),
                        reason.isBlank() ? "" : "\n        hint: " + reason
                );
                spec.commandLine().getErr().flush();
                continue;
            }
            Map<String, Long> strategies = new LinkedHashMap<>();
            Map<String, Long> timingsMs = new LinkedHashMap<>();
            strategies.put("original", fileSize(png));
            timingsMs.put("original", 0L);

            List<PngFilter> originalFilters = inspector.listFilters(png, raw);
            FilteredImage rewrittenBaseline = buildBaseline(raw, originalFilters, candidates);
            long rewrittenBaselineSize = estimateDeflatedSize(rewrittenBaseline);
            strategies.put("rewritten-baseline", rewrittenBaselineSize);
            timingsMs.put("rewritten-baseline", 0L);

            Path tmpDir = tempDir();
            if (optimizerSelection.zopflipngPath != null) {
                ZopfliRunner runner = new ZopfliRunner();
                long t0 = System.nanoTime();
                strategies.put("zopflipng-default-original", runner.recompress(png, tmpDir.resolve("zdefault.png"), optimizerSelection.zopflipngPath, false));
                timingsMs.put("zopflipng-default-original", nanosToMillis(System.nanoTime() - t0));
                t0 = System.nanoTime();
                strategies.put("zopflipng-preserve-original-filters", runner.recompress(png, tmpDir.resolve("zpreserve-original.png"), optimizerSelection.zopflipngPath, true));
                timingsMs.put("zopflipng-preserve-original-filters", nanosToMillis(System.nanoTime() - t0));
                Path rewrittenPath = tmpDir.resolve("rewritten.png");
                encoder.encode(rewrittenBaseline, rewrittenPath);
                t0 = System.nanoTime();
                strategies.put("rewritten+zopfli-preserve", runner.recompress(rewrittenPath, tmpDir.resolve("rewritten-zopfli.png"), optimizerSelection.zopflipngPath, true));
                timingsMs.put("rewritten+zopfli-preserve", nanosToMillis(System.nanoTime() - t0));
            }

            Map<String, Object> strategyDiagnostics = new LinkedHashMap<>();
            Map<String, FilterLayout> filterLayouts = new LinkedHashMap<>();
            filterLayouts.put("original", FilterLayout.fromRows(originalFilters));
            filterLayouts.put("rewritten-baseline", FilterLayout.fromRows(filtersOf(rewrittenBaseline)));
            for (CliOptions.OptimizerName name : selected) {
                String key = name.name().toLowerCase().replace('_', '-');
                long t0 = System.nanoTime();
                FilteredImage optimized = name == CliOptions.OptimizerName.BASELINE ? rewrittenBaseline : optimizers.get(name).optimize(raw, candidates);
                timingsMs.put(key, nanosToMillis(System.nanoTime() - t0));
                strategies.put(key, estimateDeflatedSize(optimized));
                filterLayouts.put(key, FilterLayout.fromRows(filtersOf(optimized)));
                strategyDiagnostics.put(key, diagnosticsCalculator.calculate(optimized));
            }

            if (strategies.containsKey("rewritten+zopfli-preserve") && strategies.containsKey("zopflipng-preserve-original-filters")) {
                strategies.put("delta-our-vs-original-filters", strategies.get("rewritten+zopfli-preserve") - strategies.get("zopflipng-preserve-original-filters"));
            }
            if (strategies.containsKey("zopflipng-default-original") && strategies.containsKey("zopflipng-preserve-original-filters")) {
                strategies.put("delta-zopfli-default-vs-preserve", strategies.get("zopflipng-default-original") - strategies.get("zopflipng-preserve-original-filters"));
            }

            Map<String, Object> filterLayoutJson = toFilterLayoutJson(filterLayouts);
            Map<String, Map<String, Object>> visualizationJson = writeFilterVisualizations(png, imageLabel(png), filterLayouts, visualizationOutputDirectory());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("image", imageLabel(png)); row.put("strategies", strategies); row.put("timings_ms", timingsMs); row.put("best", bestKey(strategies, timingsMs));
            row.put("metadata", Map.of("width", raw.width(), "height", raw.height(), "bytes_per_pixel", raw.bytesPerPixel(), "bytes_per_row", raw.bytesPerRow(), "original_color_type", raw.colorType(), "rewritten_color_type", raw.colorType(), "original_bit_depth", raw.bitDepth(), "rewritten_bit_depth", raw.bitDepth(), "palette_preserved", raw.paletteRgb() != null, "interlace_preserved", raw.interlaceMethod() == 0 || raw.interlaceMethod() == 1));
            row.put("filter_layouts", filterLayoutJson);
            if (!visualizationJson.isEmpty()) row.put("filter_visualizations", visualizationJson);
            row.put("diagnostics", strategyDiagnostics);
            images.add(row);
        }
        String markdown = renderMarkdown(images);
        if (!skipped.isEmpty()) {
            markdown += "\nSkipped files (decode errors):\n" + skipped.stream().map(s -> "- " + s).collect(Collectors.joining("\n")) + "\n";
        }
        String json = renderJson(images);
        spec.commandLine().getOut().print(markdown);
        spec.commandLine().getOut().flush();
        writeIfRequested(markdownOutput, markdown); writeIfRequested(jsonOutput, json);
    }

    private List<Path> benchmarkTargets() {
        if (singleFile == null) return discoverPngFiles(directory);
        Path candidate = singleFile.isAbsolute() ? singleFile : directory.resolve(singleFile).normalize();
        if (!Files.exists(candidate) || !Files.isRegularFile(candidate)) {
            throw new ParameterException(spec.commandLine(), "--file does not point to a readable file: " + singleFile);
        }
        if (!candidate.getFileName().toString().toLowerCase().endsWith(".png")) {
            throw new ParameterException(spec.commandLine(), "--file must point to a .png file: " + singleFile);
        }
        return List.of(candidate);
    }

    private static String bestKey(Map<String, Long> sizes, Map<String, Long> timingsMs){
        return sizes.entrySet().stream()
                .filter(e -> !e.getKey().startsWith("delta-"))
                .min(Comparator
                        .comparingLong(Map.Entry<String, Long>::getValue)
                        .thenComparingInt(e -> isGeneticDuplicateOfFixed(e.getKey(), sizes) ? 1 : 0)
                        .thenComparingLong(e -> timingsMs.getOrDefault(e.getKey(), Long.MAX_VALUE))
                        .thenComparing(Map.Entry::getKey))
                .orElseThrow()
                .getKey();
    }

    private static boolean isGeneticDuplicateOfFixed(String key, Map<String, Long> sizes) {
        if (!"genetic".equals(key)) return false;
        Long geneticSize = sizes.get("genetic");
        if (geneticSize == null) return false;
        return sizes.entrySet().stream()
                .anyMatch(e -> e.getKey().startsWith("fixed-") && Objects.equals(e.getValue(), geneticSize));
    }
    private Path visualizationOutputDirectory() {
        Path base = markdownOutput != null && markdownOutput.getParent() != null
                ? markdownOutput.getParent()
                : Path.of("build", "reports", "pngfilteropt");
        return base.resolve("filter-visualizations");
    }

    private Map<String, Map<String, Object>> writeFilterVisualizations(Path png, String imageLabel, Map<String, FilterLayout> layouts, Path outputDir) {
        Map<String, Map<String, Object>> visualizations = new LinkedHashMap<>();
        var writer = new FilterVisualizationWriter(filterVisualizationMaxSide);
        for (var e : layouts.entrySet()) {
            if (e.getValue().isTrivial()) continue;
            String fileName = visualizationFileName(imageLabel, e.getKey());
            FilterVisualizationWriter.Visualization visualization = writer.write(png, e.getValue(), outputDir.resolve(fileName));
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("path", visualization.path().toString());
            json.put("markdown_src", markdownVisualizationSource(visualization.path()));
            json.put("bytes", visualization.bytes());
            visualizations.put(e.getKey(), json);
        }
        return visualizations;
    }

    private String markdownVisualizationSource(Path visualizationPath) {
        if (markdownOutput == null || markdownOutput.getParent() == null) return visualizationPath.toString();
        return markdownOutput.getParent().toAbsolutePath().normalize().relativize(visualizationPath.toAbsolutePath().normalize()).toString();
    }

    private static Map<String, Object> toFilterLayoutJson(Map<String, FilterLayout> layouts) {
        Map<String, Object> json = new LinkedHashMap<>();
        for (var e : layouts.entrySet()) {
            FilterLayout layout = e.getValue();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("row_count", layout.rowCount());
            value.put("filter_counts", layout.counts());
            value.put("row_filters", layout.rowFilters().stream().map(Enum::name).toList());
            value.put("runs", layout.runs().stream().map(run -> Map.of(
                    "start_row", run.startRow(),
                    "end_row", run.endRowInclusive(),
                    "row_count", run.rowCount(),
                    "filter", run.filter().name()
            )).toList());
            json.put(e.getKey(), value);
        }
        return json;
    }

    private static List<PngFilter> filtersOf(FilteredImage image) {
        return image.rows().stream().map(FilteredRow::filter).toList();
    }

    private static String visualizationFileName(String imageLabel, String strategy) {
        return sanitizeFileName(imageLabel)
                + "--"
                + shortStableHash(imageLabel)
                + "--"
                + sanitizeFileName(strategy)
                + ".filters.png";
    }

    private static String sanitizeFileName(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return sanitized.isBlank() ? "image" : sanitized;
    }

    private static String shortStableHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                hash.append(String.format("%02x", digest[i] & 0xFF));
            }
            return hash.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    private String imageLabel(Path png) {
        try {
            return directory.relativize(png).toString();
        } catch (IllegalArgumentException e) {
            return png.toString();
        }
    }
    private static FilteredImage buildBaseline(RawImage raw, List<PngFilter> inputFilters, CandidateGenerator candidates){ List<FilteredRow> rows=new ArrayList<>(raw.height()); for(int y=0;y<raw.height();y++){PngFilter f=inputFilters.get(y); rows.add(candidates.generateCandidates(raw,y).stream().filter(c->c.filter()==f).findFirst().orElseThrow());} return new FilteredImage(raw, rows);}    
    private static Path tempDir(){ try{return Files.createTempDirectory("bench-png");}catch(IOException e){throw new IllegalStateException(e);} }
    private static long estimateDeflatedSize(FilteredImage image) { try { ByteArrayOutputStream raw = new ByteArrayOutputStream(); for (var row : image.rows()) { raw.write(row.filter().pngValue()); raw.write(row.filteredBytes()); } ByteArrayOutputStream compressed = new ByteArrayOutputStream(); try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) { deflater.write(raw.toByteArray()); } return compressed.size(); } catch (IOException e) { throw new IllegalStateException(e); } }
    public static List<Path> discoverPngFiles(Path root) { try (Stream<Path> stream = Files.walk(root)) { return stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png")).sorted().toList(); } catch (IOException e) { throw new IllegalStateException(e); } }
    private static long fileSize(Path p){try{return Files.size(p);}catch(IOException e){throw new IllegalStateException(e);}}
    private static String detectNonPngHint(Path p) {
        byte[] head = new byte[12];
        int read;
        try (InputStream in = Files.newInputStream(p)) {
            read = in.read(head);
        } catch (IOException e) {
            return "";
        }

        if (read >= 12
                && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "file header looks like WebP (RIFF....WEBP), not PNG";
        }
        if (read >= 8
                && !(head[0] == (byte) 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47
                && head[4] == 0x0D && head[5] == 0x0A && head[6] == 0x1A && head[7] == 0x0A)) {
            return "PNG signature missing/invalid";
        }
        return "";
    }
    private static long nanosToMillis(long nanos) { return Math.max(0L, nanos / 1_000_000L); }

    private static String formatNumber(long value) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) out.append('\u2007');
            out.append(digits.charAt(i));
        }
        return value < 0 ? "-" + out : out.toString();
    }

    private static String renderMarkdown(List<Map<String, Object>> images){
        if(images.isEmpty()) return "| Image | Best strategy |\n|---|---|\n";

        StringBuilder sb=new StringBuilder("## Benchmark summary\n\n");
        sb.append("Original = input PNG size on disk. Rewritten baseline = the same per-row filters rebuilt through this tool and then DEFLATE-estimated; this isolates rewrite/stream effects from filter-choice changes.\n\n");
        sb.append("Compression-case guide: smaller numbers are better, and a gap between `original` and `rewritten-baseline` means the rewritten IDAT stream changed compression behavior even with equivalent row filters.\n\n");
        sb.append("| Image | Best strategy |\n|---|---|\n");
        for(var image:images){
            sb.append("| [").append(image.get("image")).append("](#").append(anchor(image.get("image").toString())).append(") | ").append(image.get("best")).append(" |\n");
        }
        sb.append("\n");
        MarkdownDiagnosticsRenderer diagnosticsRenderer = new MarkdownDiagnosticsRenderer();
        for(var image:images){
            sb.append("<a id=\"").append(anchor(image.get("image").toString())).append("\"></a>\n");
            sb.append("### ").append(image.get("image")).append("\n\n");
            appendMetadata(sb, image);
            appendStrategyResults(sb, image);
            diagnosticsRenderer.appendDiagnostics(sb, image, true);
            @SuppressWarnings("unchecked") Map<String, Map<String, Object>> visualizations=(Map<String, Map<String, Object>>)image.get("filter_visualizations");
            if(visualizations!=null && !visualizations.isEmpty()){
                sb.append("Filter layout previews (row tint: NONE red, SUB orange, UP blue, AVERAGE green, PAETH purple):\n\n");
                for (var e : visualizations.entrySet()) {
                    Object src = e.getValue().getOrDefault("markdown_src", e.getValue().get("path"));
                    sb.append("**").append(e.getKey()).append("**\n\n");
                    sb.append("![](").append(src).append(")\n\n");
                }
            }
        }
        return sb.toString();
    }

    private static void appendMetadata(StringBuilder sb, Map<String, Object> image) {
        @SuppressWarnings("unchecked") Map<String, Object> metadata=(Map<String, Object>)image.get("metadata");
        sb.append("PNG metadata: width=").append(metadata.get("width"))
                .append(", height=").append(metadata.get("height"))
                .append(", color type=").append(metadata.get("original_color_type"))
                .append(", bit depth=").append(metadata.get("original_bit_depth"))
                .append(", bytes per pixel=").append(metadata.get("bytes_per_pixel"))
                .append(", bytes per row=").append(metadata.get("bytes_per_row"))
                .append(", palette preserved=").append(metadata.get("palette_preserved"))
                .append(", interlace preserved=").append(metadata.get("interlace_preserved")).append("\n\n");
    }

    private static void appendStrategyResults(StringBuilder sb, Map<String, Object> image) {
        @SuppressWarnings("unchecked") Map<String, Long> sizes=(Map<String, Long>)image.get("strategies");
        @SuppressWarnings("unchecked") Map<String, Long> timings=(Map<String, Long>)image.get("timings_ms");
        @SuppressWarnings("unchecked") Map<String, Map<String, Object>> layouts=(Map<String, Map<String, Object>>)image.get("filter_layouts");
        String best=(String)image.get("best");
        long bestSize=sizes.get(best);
        sb.append("| Strategy | Size (bytes) | Ratio vs best | NONE | SUB | UP | AVERAGE | PAETH | Time (ms) |\n");
        sb.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (var e : sizes.entrySet()) {
            if (e.getKey().startsWith("delta-")) continue;
            boolean winner=e.getKey().equals(best);
            String strong=winner ? "<strong>" : "";
            String endStrong=winner ? "</strong>" : "";
            sb.append("| ").append(strong).append(e.getKey()).append(endStrong)
                    .append(" | ").append(strong).append(formatNumber(e.getValue())).append(endStrong)
                    .append(" | ").append(strong).append(String.format("%.2f%%", 100.0 * e.getValue() / bestSize)).append(endStrong);
            @SuppressWarnings("unchecked") Map<PngFilter, Integer> counts=layouts.containsKey(e.getKey()) ? (Map<PngFilter, Integer>)layouts.get(e.getKey()).get("filter_counts") : Map.of();
            for (PngFilter filter : PngFilter.values()) sb.append(" | ").append(strong).append(counts.getOrDefault(filter, 0)).append(endStrong);
            sb.append(" | ").append(strong).append(timings.getOrDefault(e.getKey(), 0L)).append(endStrong).append(" |\n");
        }
        List<String> deltas=sizes.entrySet().stream().filter(e -> e.getKey().startsWith("delta-")).map(e -> e.getKey() + "=" + formatNumber(e.getValue()) + " bytes").toList();
        if (!deltas.isEmpty()) sb.append("\nControl deltas: ").append(String.join(", ", deltas)).append(".\n");
        sb.append("\n");
    }

    private static String anchor(String image) {
        return "image-" + sanitizeFileName(image).toLowerCase(Locale.ROOT) + "-" + shortStableHash(image);
    }

    private static String renderJson(List<Map<String, Object>> images){ try { return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("images", jsonImages(images)))+"\n"; } catch (JsonProcessingException e) { throw new IllegalStateException(e); } }

    private static List<Map<String, Object>> jsonImages(List<Map<String, Object>> images) {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> image : images) {
            Map<String, Object> imageCopy = new LinkedHashMap<>(image);
            @SuppressWarnings("unchecked") Map<String, Map<String, Object>> visualizations = (Map<String, Map<String, Object>>) imageCopy.get("filter_visualizations");
            if (visualizations != null) {
                Map<String, Map<String, Object>> visualizationCopy = new LinkedHashMap<>();
                for (var e : visualizations.entrySet()) {
                    Map<String, Object> entryCopy = new LinkedHashMap<>(e.getValue());
                    entryCopy.remove("data_uri");
                    visualizationCopy.put(e.getKey(), entryCopy);
                }
                imageCopy.put("filter_visualizations", visualizationCopy);
            }
            copy.add(imageCopy);
        }
        return copy;
    }
    private static void writeIfRequested(Path output, String content) { if (output == null) return; try { if (output.getParent() != null) Files.createDirectories(output.getParent()); Files.writeString(output, content); } catch (IOException e) { throw new IllegalStateException(e); } }
}
