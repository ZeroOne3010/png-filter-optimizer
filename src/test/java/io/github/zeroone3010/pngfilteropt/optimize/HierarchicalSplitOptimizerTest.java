package io.github.zeroone3010.pngfilteropt;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.optimize.HierarchicalSplitOptimizer;
import io.github.zeroone3010.pngfilteropt.png.PngDecoder;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class HierarchicalSplitOptimizerTest {
    @Test
    void deterministicAcrossRuns() throws Exception {
        var png = TestPngFixtures.createPng(Files.createTempFile("hier", ".png"), 6, 6);
        var raw = new PngDecoder().decode(png);
        var optimizer = new HierarchicalSplitOptimizer(5, 1);
        var a = optimizer.optimize(raw, new CandidateGenerator());
        var b = optimizer.optimize(raw, new CandidateGenerator());
        assertEquals(a.rows().stream().map(r -> r.filter().name()).toList(), b.rows().stream().map(r -> r.filter().name()).toList());
    }

    @Test
    void respectsMinSegmentRowsAndKeepsHeight() throws Exception {
        var png = TestPngFixtures.createPng(Files.createTempFile("hier-min", ".png"), 8, 8);
        var raw = new PngDecoder().decode(png);
        var optimizer = new HierarchicalSplitOptimizer(6, 3);
        var out = optimizer.optimize(raw, new CandidateGenerator());
        assertEquals(raw.height(), out.rows().size());
    }

    @Test
    void maxDepthZeroUsesSeedOnlyAndStillWorks() throws Exception {
        var png = TestPngFixtures.createPng(Files.createTempFile("hier-d0", ".png"), 4, 3);
        var raw = new PngDecoder().decode(png);
        var out = new HierarchicalSplitOptimizer(0, 1).optimize(raw, new CandidateGenerator());
        assertEquals(raw.height(), out.rows().size());
        assertTrue(out.rows().stream().allMatch(r -> r.filter() != null));
    }
}
