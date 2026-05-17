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
import io.github.zeroone3010.pngfilteropt.optimize.SumAbsOptimizer;
import io.github.zeroone3010.pngfilteropt.png.*;
import io.github.zeroone3010.pngfilteropt.report.MarkdownDiagnosticsRenderer;
import io.github.zeroone3010.pngfilteropt.zopfli.ZopfliRunner;
import picocli.CommandLine.*;
import picocli.CommandLine.Model.CommandSpec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
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

    @Override public void run() {
        var decoder = new PngDecoder(); var encoder = new PngEncoder(); var inspector = new FilterInspector(); var candidates = new CandidateGenerator();
        var selected = optimizerSelection.tryAll ? List.of(CliOptions.OptimizerName.values()) : Arrays.asList(optimizerSelection.optimizers);
        var optimizers = Map.of(
                CliOptions.OptimizerName.ENTROPY, new EntropyOptimizer(), CliOptions.OptimizerName.ADAPTIVE, new SumAbsOptimizer(),
                CliOptions.OptimizerName.EXHAUSTIVE, new LzBeamOptimizer(optimizerSelection.beamWidth), CliOptions.OptimizerName.FIXED_NONE, new FixedFilterOptimizer(PngFilter.NONE),
                CliOptions.OptimizerName.FIXED_SUB, new FixedFilterOptimizer(PngFilter.SUB), CliOptions.OptimizerName.FIXED_UP, new FixedFilterOptimizer(PngFilter.UP),
                CliOptions.OptimizerName.FIXED_AVERAGE, new FixedFilterOptimizer(PngFilter.AVERAGE), CliOptions.OptimizerName.FIXED_PAETH, new FixedFilterOptimizer(PngFilter.PAETH));

        List<Map<String, Object>> images = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        var diagnosticsCalculator = new DiagnosticsCalculator(diagnosticsLz, diagnosticsLzMaxCandidates);
        for (Path png : discoverPngFiles(directory)) {
            RawImage raw;
            try {
                raw = decoder.decode(png);
            } catch (RuntimeException e) {
                String reason = detectNonPngHint(png);
                String relative = directory.relativize(png).toString();
                skipped.add(relative + " :: " + e.getClass().getSimpleName() + (reason.isBlank() ? "" : " (" + reason + ")"));
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
            strategies.put("original", fileSize(png));

            FilteredImage rewrittenBaseline = buildBaseline(raw, inspector.listFilters(png, raw), candidates);
            long rewrittenBaselineSize = estimateDeflatedSize(rewrittenBaseline);
            strategies.put("rewritten-baseline", rewrittenBaselineSize);

            Path tmpDir = tempDir();
            if (optimizerSelection.zopflipngPath != null && benchmarkControls && benchmarkZopfliOriginal) {
                ZopfliRunner runner = new ZopfliRunner();
                strategies.put("zopflipng-default-original", runner.recompress(png, tmpDir.resolve("zdefault.png"), optimizerSelection.zopflipngPath, false));
                if (benchmarkPreserveOriginalFilters) {
                    strategies.put("zopflipng-preserve-original-filters", runner.recompress(png, tmpDir.resolve("zpreserve-original.png"), optimizerSelection.zopflipngPath, true));
                }
                Path rewrittenPath = tmpDir.resolve("rewritten.png");
                encoder.encode(rewrittenBaseline, rewrittenPath);
                strategies.put("rewritten+zopfli-preserve", runner.recompress(rewrittenPath, tmpDir.resolve("rewritten-zopfli.png"), optimizerSelection.zopflipngPath, true));
            }

            Map<String, Object> strategyDiagnostics = new LinkedHashMap<>();
            for (CliOptions.OptimizerName name : selected) {
                String key = name.name().toLowerCase().replace('_', '-');
                FilteredImage optimized = name == CliOptions.OptimizerName.BASELINE ? rewrittenBaseline : optimizers.get(name).optimize(raw, candidates);
                strategies.put(key, estimateDeflatedSize(optimized));
                if (diagnostics) strategyDiagnostics.put(key, diagnosticsCalculator.calculate(optimized));
            }

            if (strategies.containsKey("rewritten+zopfli-preserve") && strategies.containsKey("zopflipng-preserve-original-filters")) {
                strategies.put("delta-our-vs-original-filters", strategies.get("rewritten+zopfli-preserve") - strategies.get("zopflipng-preserve-original-filters"));
            }
            if (strategies.containsKey("zopflipng-default-original") && strategies.containsKey("zopflipng-preserve-original-filters")) {
                strategies.put("delta-zopfli-default-vs-preserve", strategies.get("zopflipng-default-original") - strategies.get("zopflipng-preserve-original-filters"));
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("image", directory.relativize(png).toString()); row.put("strategies", strategies); row.put("best", bestKey(strategies));
            row.put("metadata", Map.of("original_color_type", raw.colorType(), "rewritten_color_type", raw.colorType(), "original_bit_depth", raw.bitDepth(), "rewritten_bit_depth", raw.bitDepth(), "palette_preserved", raw.paletteRgb() != null, "interlace_preserved", raw.interlaceMethod() == 0 || raw.interlaceMethod() == 1));
            row.put("interpretation", interpretations(strategies));
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

    private static List<String> interpretations(Map<String, Long> s){ List<String> out=new ArrayList<>();
        if(has(s,"zopflipng-default-original","original")&&s.get("zopflipng-default-original")+16<s.get("original")) out.add("Case A: Zopfli default beats original significantly; source encoder likely weak.");
        if(has(s,"zopflipng-preserve-original-filters","zopflipng-default-original")&&Math.abs(s.get("zopflipng-preserve-original-filters")-s.get("zopflipng-default-original"))<=16) out.add("Case B: Original filters are already strong under Zopfli.");
        if(has(s,"rewritten+zopfli-preserve","zopflipng-preserve-original-filters")&&s.get("rewritten+zopfli-preserve")>s.get("zopflipng-preserve-original-filters")) out.add("Case C: Our filters underperform original filters under equal Zopfli recompression.");
        if(has(s,"rewritten-baseline","original")&&Math.abs(s.get("rewritten-baseline")-s.get("original"))>64) out.add("Case D: Rewriting changes compression characteristics.");
        if(has(s,"rewritten+zopfli-preserve","zopflipng-preserve-original-filters")&&s.get("rewritten+zopfli-preserve")<s.get("zopflipng-preserve-original-filters")) out.add("Case E: Our filters improve over original filters under equal Zopfli recompression.");
        return out; }
    private static boolean has(Map<String, Long>s,String a,String b){return s.containsKey(a)&&s.containsKey(b);}    
    private static String bestKey(Map<String, Long> s){ return s.entrySet().stream().filter(e->!e.getKey().startsWith("delta-")).min(Comparator.comparingLong(Map.Entry::getValue)).orElseThrow().getKey(); }
    private static FilteredImage buildBaseline(RawImage raw, List<PngFilter> inputFilters, CandidateGenerator candidates){ List<FilteredRow> rows=new ArrayList<>(raw.height()); for(int y=0;y<raw.height();y++){PngFilter f=inputFilters.get(y); rows.add(candidates.generateCandidates(raw,y).stream().filter(c->c.filter()==f).findFirst().orElseThrow());} return new FilteredImage(raw, rows);}    
    private static Path tempDir(){ try{return Files.createTempDirectory("bench-png");}catch(IOException e){throw new IllegalStateException(e);} }
    private static long estimateDeflatedSize(FilteredImage image) { try { ByteArrayOutputStream raw = new ByteArrayOutputStream(); for (var row : image.rows()) { raw.write(row.filter().pngValue()); raw.write(row.filteredBytes()); } ByteArrayOutputStream compressed = new ByteArrayOutputStream(); try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) { deflater.write(raw.toByteArray()); } return compressed.size(); } catch (IOException e) { throw new IllegalStateException(e); } }
    public static List<Path> discoverPngFiles(Path root) { try (Stream<Path> stream = Files.walk(root)) { return stream.filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png")).sorted().toList(); } catch (IOException e) { throw new IllegalStateException(e); } }
    private static long fileSize(Path p){try{return Files.size(p);}catch(IOException e){throw new IllegalStateException(e);}}
    private static String detectNonPngHint(Path p) {
        try {
            byte[] head = Files.readAllBytes(p);
            if (head.length >= 12
                    && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
                return "file header looks like WebP (RIFF....WEBP), not PNG";
            }
            if (head.length >= 8
                    && !(head[0] == (byte) 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47
                    && head[4] == 0x0D && head[5] == 0x0A && head[6] == 0x1A && head[7] == 0x0A)) {
                return "PNG signature missing/invalid";
            }
            return "";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    private static String renderMarkdown(List<Map<String, Object>> images){ if(images.isEmpty()) return "| image | original | best |\n|---|---:|---|\n"; @SuppressWarnings("unchecked") Map<String,Long> first=(Map<String,Long>)images.get(0).get("strategies"); List<String> cols=new ArrayList<>(first.keySet()); cols.remove("original"); StringBuilder sb=new StringBuilder("| Image | Original"); for(String c:cols) sb.append(" | ").append(c); sb.append(" | Best |\n|---|---:"); for(int i=0;i<cols.size();i++) sb.append("|---:"); sb.append("|---|\n"); for(var image:images){ @SuppressWarnings("unchecked") Map<String,Long> s=(Map<String,Long>)image.get("strategies"); sb.append("| ").append(image.get("image")).append(" | ").append(s.get("original")); for(String c:cols) sb.append(" | ").append(s.get(c)); sb.append(" | ").append(image.get("best")).append(" |\n"); @SuppressWarnings("unchecked") List<String> interp=(List<String>)image.get("interpretation"); if(!interp.isEmpty()) sb.append("| ↳ interpretation | ").append(String.join("; ", interp)).append(" |").append(" |".repeat(cols.size()+1)).append("\n"); }
        return sb.toString(); }
    private static String renderJson(List<Map<String, Object>> images){ try { return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("images",images))+"\n"; } catch (JsonProcessingException e) { throw new IllegalStateException(e); } }
    private static void writeIfRequested(Path output, String content) { if (output == null) return; try { if (output.getParent() != null) Files.createDirectories(output.getParent()); Files.writeString(output, content); } catch (IOException e) { throw new IllegalStateException(e); } }
}
