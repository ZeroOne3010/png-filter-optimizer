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
        Path json = Files.createTempFile("bench", ".json");
        int exit = new CommandLine(new io.github.zeroone3010.pngfilteropt.Main()).execute(
                "benchmark", root.toString(), "--markdown", md.toString(), "--json", json.toString()
        );
        assertEquals(0, exit);
        String mdText = Files.readString(md);
        String jsonText = Files.readString(json);
        assertTrue(mdText.contains("| image | original | fixed-none | sumabs | best |"));
        var json = new ObjectMapper().readTree(jsonText);
        assertTrue(json.has("images"));
        assertTrue(json.get("summary").has("best_vs_original_pct"));
        assertTrue(json.get("summary").has("best_vs_sumabs_pct"));
        assertTrue(json.get("summary").has("best_vs_zopflipng_default_pct"));
    }
}
