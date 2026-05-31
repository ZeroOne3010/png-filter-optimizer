package io.github.zeroone3010.pngfilteropt;

import io.github.zeroone3010.pngfilteropt.cli.BenchmarkCommand;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class CliBehaviorTest {
    @Test
    void optimizeRunsNamedOptimizerAndWritesOutput() throws Exception {
        Path input = TestPngFixtures.createPng(Files.createTempFile("opt", ".png"), 2, 2);
        Path output = Files.createTempFile("opt-out", ".png");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int exit = new CommandLine(new io.github.zeroone3010.pngfilteropt.Main())
                .setOut(new java.io.PrintWriter(out, true))
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute("optimize", input.toString(), output.toString(), "--optimizer", "entropy");

        assertEquals(0, exit);
        assertTrue(Files.size(output) > 0);
        assertTrue(out.toString().contains("strategy="));
    }

    @Test
    void optimizeTryAllChoosesStrategyAndPrintsSummary() throws Exception {
        Path input = TestPngFixtures.createPng(Files.createTempFile("opt-all", ".png"), 3, 2);
        Path output = Files.createTempFile("opt-all-out", ".png");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int exit = new CommandLine(new io.github.zeroone3010.pngfilteropt.Main())
                .setOut(new java.io.PrintWriter(out, true))
                .execute("optimize", input.toString(), output.toString(), "--try-all", "--beam", "8");

        assertEquals(0, exit);
        assertTrue(Files.size(output) > 0);
        assertTrue(out.toString().contains("output_bytes="));
    }


    @Test
    void optimizeSupportsGeneticOptimizer() throws Exception {
        Path input = TestPngFixtures.createPng(Files.createTempFile("opt-ga", ".png"), 4, 4);
        Path output = Files.createTempFile("opt-ga-out", ".png");

        int exit = new CommandLine(new io.github.zeroone3010.pngfilteropt.Main())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute("optimize", input.toString(), output.toString(), "--optimizer", "genetic", "--ga-seed", "7", "--ga-time-limit-ms", "1000");

        assertEquals(0, exit);
        assertTrue(Files.size(output) > 0);
    }
    @Test
    void optimizeWithZopfliKeepsSmallerResult() throws Exception {
        Path input = TestPngFixtures.createPng(Files.createTempFile("opt-z", ".png"), 3, 3);
        Path output = Files.createTempFile("opt-z-out", ".png");
        Path script = Files.createTempFile("fake-zopfli", ".sh");
        Files.writeString(script, "#!/usr/bin/env bash\nset -euo pipefail\ncp \"$3\" \"$4\"\ntruncate -s 20 \"$4\"\n");
        script.toFile().setExecutable(true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = new CommandLine(new io.github.zeroone3010.pngfilteropt.Main())
                .setOut(new java.io.PrintWriter(out, true))
                .execute("optimize", input.toString(), output.toString(), "--zopflipng", script.toString());

        assertEquals(0, exit);
        assertEquals(20, Files.size(output));
        assertTrue(out.toString().contains("zopfli_bytes=20"));
    }

    @Test
    void inspectOutputsRowFilterCsv() throws Exception {
        Path png = TestPngFixtures.createPng(Files.createTempFile("inspect", ".png"), 1, 1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = new CommandLine(new io.github.zeroone3010.pngfilteropt.Main())
                .setOut(new java.io.PrintWriter(out, true))
                .execute("inspect", png.toString());
        String text = out.toString();
        assertEquals(0, exit);
        assertTrue(text.contains("row,filter"));
        assertTrue(text.contains("0,"));
    }

    @Test
    void benchmarkRecursivelyFindsPngAndWritesOutputs() throws Exception {
        Path root = Files.createTempDirectory("png-bench");
        TestPngFixtures.createPng(root.resolve("a/one.png"), 1, 1);
        TestPngFixtures.createPng(root.resolve("b/nested/two.png"), 2, 1);

        var found = BenchmarkCommand.discoverPngFiles(root);
        assertEquals(2, found.size());

        Path md = Files.createTempFile("bench", ".md");
        Path jsonPath = Files.createTempFile("bench", ".json");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = new CommandLine(new io.github.zeroone3010.pngfilteropt.Main())
                .setOut(new java.io.PrintWriter(out, true))
                .execute("benchmark", root.toString(), "--try-all", "--markdown", md.toString(), "--json", jsonPath.toString());
        assertEquals(0, exit);
        String mdText = Files.readString(md);
        String jsonText = Files.readString(jsonPath);
        assertTrue(mdText.contains("| Image | Best strategy |"));
        assertTrue(mdText.contains("rewritten-baseline"));
        assertTrue(mdText.contains("Best"));
        var json = new ObjectMapper().readTree(jsonText);
        assertTrue(json.has("images"));
        assertTrue(json.get("images").get(0).has("metadata"));
        assertTrue(json.get("images").get(0).has("timings_ms"));
        var sourceImage = json.get("images").get(0).get("source_image");
        assertNotNull(sourceImage);
        assertTrue(Files.isRegularFile(Path.of(sourceImage.get("path").asText())));
        assertTrue(mdText.contains("![Source image: a/one.png](source-images/"));
        assertTrue(mdText.contains("| Strategy | Size (bytes) | Ratio vs best | NONE | SUB | UP | AVERAGE | PAETH | Time (ms) |"));
    }
    @Test
    void benchmarkDefaultPrintsAdaptiveStrategyLine() throws Exception {
        Path root = Files.createTempDirectory("png-bench-default");
        TestPngFixtures.createPng(root.resolve("one.png"), 1, 1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = new CommandLine(new io.github.zeroone3010.pngfilteropt.Main())
                .setOut(new java.io.PrintWriter(out, true))
                .execute("benchmark", root.toString());

        assertEquals(0, exit);
        assertTrue(out.toString().contains("| Image | Best strategy |"));
        assertTrue(out.toString().contains("adaptive"));
        assertFalse(out.toString().contains("↳ interpretation"));
    }

    @Test
    void benchmarkSupportsSingleFileSelection() throws Exception {
        Path root = Files.createTempDirectory("png-bench-single");
        TestPngFixtures.createPng(root.resolve("one.png"), 1, 1);
        TestPngFixtures.createPng(root.resolve("two.png"), 1, 1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = new CommandLine(new io.github.zeroone3010.pngfilteropt.Main())
                .setOut(new java.io.PrintWriter(out, true))
                .execute("benchmark", root.toString(), "--file", "two.png");

        assertEquals(0, exit);
        String text = out.toString();
        assertTrue(text.contains("| [two.png](#image-two.png-"));
        assertFalse(text.contains("[one.png](#image-one.png-"));
    }

    @Test
    void benchmarkSupportsAbsoluteSingleFilePath() throws Exception {
        Path root = Files.createTempDirectory("png-bench-single-abs");
        Path one = TestPngFixtures.createPng(root.resolve("one.png"), 1, 1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = new CommandLine(new io.github.zeroone3010.pngfilteropt.Main())
                .setOut(new java.io.PrintWriter(out, true))
                .execute("benchmark", "--file", one.toAbsolutePath().toString());

        assertEquals(0, exit);
        assertTrue(out.toString().contains("[" + one.toAbsolutePath() + "](#image-"));
    }



    @Test
    void benchmarkWithZopfliPrintsZopfliInStrategyLine() throws Exception {
        Path root = Files.createTempDirectory("png-bench-z");
        TestPngFixtures.createPng(root.resolve("one.png"), 1, 1);
        Path script = Files.createTempFile("fake-zopfli-bench", ".sh");
        Files.writeString(script, "#!/usr/bin/env bash\nset -euo pipefail\nin=\"${@: -2:1}\"\nout=\"${@: -1}\"\ncp \"$in\" \"$out\"\n");
        script.toFile().setExecutable(true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = new CommandLine(new io.github.zeroone3010.pngfilteropt.Main())
                .setOut(new java.io.PrintWriter(out, true))
                .execute("benchmark", root.toString(), "--zopflipng", script.toString());

        assertEquals(0, exit);
        assertTrue(out.toString().contains("zopflipng-default-original"));
    }

    @Test
    void benchmarkAlwaysWritesLocalizedMaximalReport() throws Exception {
        Path root = Files.createTempDirectory("png-bench-diag");
        TestPngFixtures.createPng(root.resolve("one.png"), 2, 2);
        Path md = Files.createTempFile("bench-diag", ".md");
        Path jsonPath = Files.createTempFile("bench-diag", ".json");

        int exit = new CommandLine(new io.github.zeroone3010.pngfilteropt.Main())
                .execute("benchmark", root.toString(), "--try-all", "--markdown", md.toString(), "--json", jsonPath.toString());

        assertEquals(0, exit);
        String mdText = Files.readString(md);
        String jsonText = Files.readString(jsonPath);
        assertFalse(mdText.contains("### Table of contents"));
        assertTrue(mdText.contains("| [one.png](#image-one.png-"));
        assertTrue(mdText.contains("PNG metadata: width=2, height=2"));
        assertTrue(mdText.contains("| Strategy | Size (bytes) | Ratio vs best | NONE | SUB | UP | AVERAGE | PAETH | Time (ms) |"));
        assertTrue(mdText.contains("<strong>"));
        assertTrue(mdText.contains("<details>"));
        assertTrue(mdText.contains("<summary>Diagnostics</summary>"));
        assertTrue(mdText.contains("Directional smoothness:"));
        assertTrue(mdText.contains("Residual sumAbs:"));
        assertTrue(mdText.contains("Likely explanation:"));
        assertTrue(mdText.contains("Compression insight:"));
        assertTrue(mdText.contains("Compression observations:"));
        assertFalse(mdText.contains("Patterns detected:"));
        assertTrue(mdText.contains("approximate LZ longest-match estimation"));
        var json = new ObjectMapper().readTree(jsonText);
        assertTrue(json.get("images").get(0).has("diagnostics"));
    }

    @Test
    void optimizeAcceptsRewrittenBaselineAndRejectsRemovedBaselineName() throws Exception {
        Path root = Files.createTempDirectory("png-optimize-rewritten-baseline");
        Path input = TestPngFixtures.createPng(root.resolve("one.png"), 2, 2);

        int rewrittenExit = new CommandLine(new io.github.zeroone3010.pngfilteropt.Main())
                .execute("optimize", input.toString(), root.resolve("rewritten.png").toString(), "--optimizer", "rewritten-baseline");
        int fixedNoneExit = new CommandLine(new io.github.zeroone3010.pngfilteropt.Main())
                .execute("optimize", input.toString(), root.resolve("fixed-none.png").toString(), "--optimizer", "fixed-none");
        int removedExit = new CommandLine(new io.github.zeroone3010.pngfilteropt.Main())
                .execute("optimize", input.toString(), root.resolve("removed.png").toString(), "--optimizer", "baseline");

        assertEquals(0, rewrittenExit);
        assertEquals(0, fixedNoneExit);
        assertEquals(2, removedExit);
    }

    @Test
    void benchmarkRejectsRemovedReportOutputFlags() throws Exception {
        Path root = Files.createTempDirectory("png-bench-removed-output-flag");
        TestPngFixtures.createPng(root.resolve("one.png"), 2, 2);

        int exit = new CommandLine(new io.github.zeroone3010.pngfilteropt.Main())
                .execute("benchmark", root.toString(), "--insights");

        assertEquals(2, exit);
    }

}
