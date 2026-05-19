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
        var optimizer = new GeneticSplitOptimizer(8, 16, 8, 0.15, 1, 123L, java.time.Duration.ofSeconds(2));
        var a = optimizer.optimize(raw, new CandidateGenerator());
        var b = optimizer.optimize(raw, new CandidateGenerator());
        assertEquals(a.rows().stream().map(r -> r.filter().name()).toList(), b.rows().stream().map(r -> r.filter().name()).toList());
    }

    @Test
    void keepsHeight() throws Exception {
        var png = TestPngFixtures.createPng(Files.createTempFile("hier-min", ".png"), 8, 8);
        var raw = new PngDecoder().decode(png);
        var optimizer = new GeneticSplitOptimizer(8, 16, 8, 0.15, 1, 123L, java.time.Duration.ofSeconds(2));
        var out = optimizer.optimize(raw, new CandidateGenerator());
        assertEquals(raw.height(), out.rows().size());
    }

    @Test
    void worksWithSmallPopulation() throws Exception {
        var png = TestPngFixtures.createPng(Files.createTempFile("hier-d0", ".png"), 4, 3);
        var raw = new PngDecoder().decode(png);
        var out = new GeneticSplitOptimizer(8, 4, 2, 0.0, 1, 123L, java.time.Duration.ofSeconds(2)).optimize(raw, new CandidateGenerator());
        assertEquals(raw.height(), out.rows().size());
        assertTrue(out.rows().stream().allMatch(r -> r.filter() != null));
    }
}
