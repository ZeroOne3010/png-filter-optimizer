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
        int exit = new CommandLine(new io.github.zeroone3010.pngfilteropt.Main()).execute(
                "benchmark", root.toString(), "--markdown", md.toString(), "--json", jsonPath.toString()
        );
        assertEquals(0, exit);
        String mdText = Files.readString(md);
        String jsonText = Files.readString(jsonPath);
        assertTrue(mdText.contains("| image | original | fixed-none | sumabs | best |"));
        var json = new ObjectMapper().readTree(jsonText);
        assertTrue(json.has("images"));
        assertTrue(json.get("summary").has("best_vs_original_pct"));
        assertTrue(json.get("summary").has("best_vs_sumabs_pct"));
        assertTrue(json.get("summary").has("best_vs_zopflipng_default_pct"));
    }
}
