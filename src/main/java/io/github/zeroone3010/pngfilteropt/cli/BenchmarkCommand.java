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
    @Option(names = "--diagnostics") boolean diagnostics;
    @Option(names = "--diagnostics-lz", negatable = true) boolean diagnosticsLz = true;
    @Option(names = "--diagnostics-lz-sample-step", defaultValue = "1") int diagnosticsLzSampleStep;
    @Option(names = "--diagnostics-lz-max-candidates", defaultValue = "16") int diagnosticsLzMaxCandidates;
    @Option(names = "--benchmark-controls", description = "Enable control/reference benchmark variants.") boolean benchmarkControls = true;
    @Option(names = "--benchmark-zopfli-original", description = "Include zopfli runs against original PNGs.") boolean benchmarkZopfliOriginal = true;
    @Option(names = "--benchmark-preserve-original-filters", description = "Include zopfli preserve-filters control runs.") boolean benchmarkPreserveOriginalFilters = true;
    @Option(names = "--filter-visualizations", negatable = true, description = "Write small palettized PNG previews that tint each row by its selected PNG filter.") boolean filterVisualizations = true;
    @Option(names = "--filter-visualization-max-side", defaultValue = "256", description = "Maximum width or height for filter visualization PNGs.") int filterVisualizationMaxSide = FilterVisualizationWriter.DEFAULT_MAX_SIDE;
    @Option(names = "--filter-visualization-inline", negatable = true, description = "Embed filter visualization PNGs as data URIs in markdown, useful for GitHub step summaries.") boolean filterVisualizationInline = false;

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
        var diagnosticsCalculator = new DiagnosticsCalculator(diagnosticsLz, diagnosticsLzMaxCandidates);
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
            if (optimizerSelection.zopflipngPath != null && benchmarkControls && benchmarkZopfliOriginal) {
                ZopfliRunner runner = new ZopfliRunner();
                long t0 = System.nanoTime();
                strategies.put("zopflipng-default-original", runner.recompress(png, tmpDir.resolve("zdefault.png"), optimizerSelection.zopflipngPath, false));
                timingsMs.put("zopflipng-default-original", nanosToMillis(System.nanoTime() - t0));
                if (benchmarkPreserveOriginalFilters) {
                    t0 = System.nanoTime();
                    strategies.put("zopflipng-preserve-original-filters", runner.recompress(png, tmpDir.resolve("zpreserve-original.png"), optimizerSelection.zopflipngPath, true));
                    timingsMs.put("zopflipng-preserve-original-filters", nanosToMillis(System.nanoTime() - t0));
                }
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
                if (diagnostics) strategyDiagnostics.put(key, diagnosticsCalculator.calculate(optimized));
            }

            if (strategies.containsKey("rewritten+zopfli-preserve") && strategies.containsKey("zopflipng-preserve-original-filters")) {
                strategies.put("delta-our-vs-original-filters", strategies.get("rewritten+zopfli-preserve") - strategies.get("zopflipng-preserve-original-filters"));
            }
            if (strategies.containsKey("zopflipng-default-original") && strategies.containsKey("zopflipng-preserve-original-filters")) {
                strategies.put("delta-zopfli-default-vs-preserve", strategies.get("zopflipng-default-original") - strategies.get("zopflipng-preserve-original-filters"));
            }

            Map<String, Object> filterLayoutJson = toFilterLayoutJson(filterLayouts);
            Map<String, Map<String, Object>> visualizationJson = filterVisualizations
                    ? writeFilterVisualizations(png, imageLabel(png), filterLayouts, shouldInlineVisualizations(), visualizationOutputDirectory())
                    : new LinkedHashMap<>();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("image", imageLabel(png)); row.put("strategies", strategies); row.put("timings_ms", timingsMs); row.put("best", bestKey(strategies, timingsMs));
            row.put("metadata", Map.of("original_color_type", raw.colorType(), "rewritten_color_type", raw.colorType(), "original_bit_depth", raw.bitDepth(), "rewritten_bit_depth", raw.bitDepth(), "palette_preserved", raw.paletteRgb() != null, "interlace_preserved", raw.interlaceMethod() == 0 || raw.interlaceMethod() == 1));
            row.put("filter_layouts", filterLayoutJson);
            if (!visualizationJson.isEmpty()) row.put("filter_visualizations", visualizationJson);
            if (diagnostics) row.put("diagnostics", strategyDiagnostics);
            images.add(row);
        }
        String markdown = renderMarkdown(images); if (diagnostics) markdown += new MarkdownDiagnosticsRenderer().render(images);
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
    private boolean shouldInlineVisualizations() {
        if (filterVisualizationInline) return true;
        String githubStepSummary = System.getenv("GITHUB_STEP_SUMMARY");
        return markdownOutput != null && githubStepSummary != null && markdownOutput.toAbsolutePath().normalize().equals(Path.of(githubStepSummary).toAbsolutePath().normalize());
    }

    private Path visualizationOutputDirectory() {
        Path base = markdownOutput != null && markdownOutput.getParent() != null
                ? markdownOutput.getParent()
                : Path.of("build", "reports", "pngfilteropt");
        return base.resolve("filter-visualizations");
    }

    private Map<String, Map<String, Object>> writeFilterVisualizations(Path png, String imageLabel, Map<String, FilterLayout> layouts, boolean inline, Path outputDir) {
        Map<String, Map<String, Object>> visualizations = new LinkedHashMap<>();
        var writer = new FilterVisualizationWriter(filterVisualizationMaxSide);
        for (var e : layouts.entrySet()) {
            if (e.getValue().isTrivial()) continue;
            String fileName = sanitizeFileName(imageLabel) + "--" + sanitizeFileName(e.getKey()) + ".filters.png";
            FilterVisualizationWriter.Visualization visualization = writer.write(png, e.getValue(), outputDir.resolve(fileName));
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("path", visualization.path().toString());
            json.put("markdown_src", markdownVisualizationSource(visualization.path()));
            json.put("bytes", visualization.bytes());
            if (inline) json.put("data_uri", visualization.dataUri());
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

    private static String sanitizeFileName(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return sanitized.isBlank() ? "image" : sanitized;
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
        if(images.isEmpty()) return "| image | original | best |\n|---|---:|---|\n";
        @SuppressWarnings("unchecked") Map<String,Long> first=(Map<String,Long>)images.get(0).get("strategies");
        List<String> cols=new ArrayList<>(first.keySet());
        cols.remove("original");
        cols.remove("baseline");

        StringBuilder sb=new StringBuilder("## Benchmark summary\n\n");
        sb.append("Original = input PNG size on disk. Rewritten baseline = the same per-row filters rebuilt through this tool and then DEFLATE-estimated; this isolates rewrite/stream effects from filter-choice changes.\n\n");
        sb.append("Compression-case guide: smaller numbers are better, and a gap between `original` and `rewritten-baseline` means the rewritten IDAT stream changed compression behavior even with equivalent row filters.\n\n");
        sb.append("### Table of contents\n");
        for(var image:images) sb.append("- [").append(image.get("image")).append("](#").append(image.get("image")).append(")\n");
        sb.append("\n| Image | Original");
        for(String c:cols) sb.append(" | ").append(c);
        sb.append(" | Best |\n|---|---:");
        for(int i=0;i<cols.size();i++) sb.append("|---:");
        sb.append("|---|\n");
        for(var image:images){
            @SuppressWarnings("unchecked") Map<String,Long> s=(Map<String,Long>)image.get("strategies");
            sb.append("| ").append(image.get("image")).append(" | ").append(formatNumber(s.get("original")));
            for(String c:cols) sb.append(" | ").append(formatNumber(s.get(c)));
            sb.append(" | ").append(image.get("best")).append(" |\n");
        }
        sb.append("\n");
        for(var image:images){
            sb.append("### ").append(image.get("image")).append("\n\n");
            @SuppressWarnings("unchecked") Map<String,Long> t=(Map<String,Long>)image.get("timings_ms");
            if(t!=null){
                sb.append("Timing (ms):\n\n");
                for (var e : t.entrySet()) sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append(" ms\n");
                sb.append("\n");
            }
            @SuppressWarnings("unchecked") Map<String, Map<String, Object>> visualizations=(Map<String, Map<String, Object>>)image.get("filter_visualizations");
            if(visualizations!=null && !visualizations.isEmpty()){
                sb.append("Filter layout previews (row tint: NONE red, SUB orange, UP blue, AVERAGE green, PAETH purple):\n\n");
                for (var e : visualizations.entrySet()) {
                    Object src = e.getValue().containsKey("data_uri") ? e.getValue().get("data_uri") : e.getValue().getOrDefault("markdown_src", e.getValue().get("path"));
                    sb.append("**").append(e.getKey()).append("**\n\n");
                    sb.append("![](").append(src).append(")\n\n");
                }
            }
        }
        return sb.toString();
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
