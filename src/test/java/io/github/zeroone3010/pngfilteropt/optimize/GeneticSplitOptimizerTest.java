package io.github.zeroone3010.pngfilteropt;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.optimize.GeneticSplitOptimizer;
import io.github.zeroone3010.pngfilteropt.png.PngDecoder;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class GeneticSplitOptimizerTest {
    @Test
    void deterministicAcrossRuns() throws Exception {
        var png = TestPngFixtures.createPng(Files.createTempFile("hier", ".png"), 6, 6);
        var raw = new PngDecoder().decode(png);
        var optimizer = new GeneticSplitOptimizer(8, 64, 16, 8, 2, 2, 0.15, 123L, 3, true, 4);
        var a = optimizer.optimize(raw, new CandidateGenerator());
        var b = optimizer.optimize(raw, new CandidateGenerator());
        assertEquals(a.rows().stream().map(r -> r.filter().name()).toList(), b.rows().stream().map(r -> r.filter().name()).toList());
    }

    @Test
    void keepsHeight() throws Exception {
        var png = TestPngFixtures.createPng(Files.createTempFile("hier-min", ".png"), 8, 8);
        var raw = new PngDecoder().decode(png);
        var optimizer = new GeneticSplitOptimizer(8, 64, 16, 8, 2, 2, 0.15, 123L, 3, true, 4);
        var out = optimizer.optimize(raw, new CandidateGenerator());
        assertEquals(raw.height(), out.rows().size());
    }

    @Test
    void budgetAndReportingPresent() throws Exception {
        var png = TestPngFixtures.createPng(Files.createTempFile("hier-budget", ".png"), 8, 8);
        var raw = new PngDecoder().decode(png);
        var optimizer = new GeneticSplitOptimizer(8, 12, 8, 4, 2, 10, 0.15, 123L, 3, true, 4);
        optimizer.optimize(raw, new CandidateGenerator());
        var log = optimizer.explainLastRun().orElseThrow();
        assertTrue(log.contains("- evaluations:"));
        assertTrue(log.contains("/ 12"));
        assertTrue(log.contains("- cache hits:"));
        assertTrue(log.contains("fast-deflate estimated fitness"));
    }

    @Test
    void worksWithSmallPopulation() throws Exception {
        var png = TestPngFixtures.createPng(Files.createTempFile("hier-d0", ".png"), 4, 3);
        var raw = new PngDecoder().decode(png);
        var out = new GeneticSplitOptimizer(8, 16, 4, 2, 1, 1, 0.0, 123L, 3, false, 1).optimize(raw, new CandidateGenerator());
        assertEquals(raw.height(), out.rows().size());
        assertTrue(out.rows().stream().allMatch(r -> r.filter() != null));
    }
}
