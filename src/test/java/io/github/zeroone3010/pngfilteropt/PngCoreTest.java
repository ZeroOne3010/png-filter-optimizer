package io.github.zeroone3010.pngfilteropt;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.optimize.FixedFilterOptimizer;
import io.github.zeroone3010.pngfilteropt.optimize.SumAbsOptimizer;
import io.github.zeroone3010.pngfilteropt.png.PngDecoder;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class PngCoreTest {
    @Test
    void decodeBasicsFixture() throws Exception {
        var png = TestPngFixtures.createPng(Files.createTempFile("one", ".png"), 1, 1);
        var image = new PngDecoder().decode(png);
        assertEquals(1, image.width());
        assertEquals(1, image.height());
        assertFalse(image.rows().isEmpty());
    }

    @Test
    void fixedFilterAppliesRequestedFilter() throws Exception {
        var png = TestPngFixtures.createPng(Files.createTempFile("one", ".png"), 2, 2);
        var image = new PngDecoder().decode(png);
        var out = new FixedFilterOptimizer(PngFilter.NONE).optimize(image, new CandidateGenerator());
        assertEquals(image.height(), out.rows().size());
        assertTrue(out.rows().stream().allMatch(r -> r.filter() == PngFilter.NONE));
    }

    @Test
    void optimizerScaffoldingProducesRows() throws Exception {
        var png = TestPngFixtures.createPng(Files.createTempFile("one", ".png"), 3, 2);
        var image = new PngDecoder().decode(png);
        var fixed = new FixedFilterOptimizer(PngFilter.SUB).optimize(image, new CandidateGenerator());
        var sumabs = new SumAbsOptimizer().optimize(image, new CandidateGenerator());
        assertEquals(image.height(), fixed.rows().size());
        assertEquals(image.height(), sumabs.rows().size());
    }
}
