package io.github.zeroone3010.pngfilteropt.png;

import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FilterVisualizationWriterTest {
    @Test
    void layoutRecordsRowsAndRuns() {
        FilterLayout layout = FilterLayout.fromRows(List.of(
                PngFilter.NONE,
                PngFilter.NONE,
                PngFilter.UP,
                PngFilter.NONE
        ));

        assertEquals(4, layout.rowCount());
        assertEquals(3, layout.runs().size());
        assertEquals(new FilterLayout.Run(0, 1, PngFilter.NONE), layout.runs().get(0));
        assertEquals(new FilterLayout.Run(2, 2, PngFilter.UP), layout.runs().get(1));
        assertFalse(layout.isTrivial());
        assertEquals(3, layout.counts().get(PngFilter.NONE));
        assertEquals(1, layout.counts().get(PngFilter.UP));
    }

    @Test
    void writesPalettizedVisualizationAtOriginalSizeForNonContiguousRows() throws Exception {
        Path input = Files.createTempFile("filter-preview", ".png");
        BufferedImage source = new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.setRGB(x, y, ((x * 20) << 16) | ((y * 20) << 8) | 80);
            }
        }
        ImageIO.write(source, "png", input.toFile());

        List<PngFilter> filters = new ArrayList<>();
        for (int y = 0; y < source.getHeight(); y++) {
            filters.add(y == 6 ? PngFilter.UP : PngFilter.NONE);
        }
        FilterLayout layout = FilterLayout.fromRows(filters);
        Path output = Files.createTempFile("filter-preview-out", ".png");

        FilterVisualizationWriter.Visualization visualization = new FilterVisualizationWriter().write(input, layout, output);

        assertTrue(Files.size(output) > 0);
        assertEquals(Files.size(output), visualization.bytes());
        assertTrue(visualization.dataUri().startsWith("data:image/png;base64,"));
        BufferedImage preview = ImageIO.read(output.toFile());
        assertNotNull(preview);
        assertEquals(source.getWidth(), preview.getWidth());
        assertEquals(source.getHeight(), preview.getHeight());
        assertEquals(BufferedImage.TYPE_BYTE_INDEXED, preview.getType());
        assertEquals(256, preview.getColorModel().getMapSize());
    }
}
