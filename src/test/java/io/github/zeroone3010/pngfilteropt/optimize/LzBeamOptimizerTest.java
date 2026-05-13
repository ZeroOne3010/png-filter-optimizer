package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.png.PngDecoder;
import io.github.zeroone3010.pngfilteropt.png.RawImage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LzBeamOptimizerTest {
    @Test
    void differentBeamWidthsCanChooseDifferentFilterSequences() throws Exception {
        CandidateGenerator candidates = new CandidateGenerator();
        PngDecoder decoder = new PngDecoder();
        Random random = new Random(123456789L);

        RawImage beamSensitive = null;
        List<Integer> narrowFilters = List.of();
        List<Integer> wideFilters = List.of();

        for (int attempt = 0; attempt < 200; attempt++) {
            Path png = createRandomFixture(random, 8, 8);
            RawImage image = decoder.decode(png);

            var narrow = new LzBeamOptimizer(1).optimize(image, candidates);
            var wide = new LzBeamOptimizer(128).optimize(image, candidates);

            List<Integer> narrowDecision = narrow.rows().stream().map(r -> r.filter().pngValue()).toList();
            List<Integer> wideDecision = wide.rows().stream().map(r -> r.filter().pngValue()).toList();

            if (!narrowDecision.equals(wideDecision)) {
                beamSensitive = image;
                narrowFilters = new ArrayList<>(narrowDecision);
                wideFilters = new ArrayList<>(wideDecision);
                break;
            }
        }

        assertTrue(beamSensitive != null, "Expected at least one fixture to produce different beam-search decisions");
        assertNotEquals(narrowFilters, wideFilters);
    }

    private static Path createRandomFixture(Random random, int width, int height) throws Exception {
        Path path = Files.createTempFile("beam-sensitive-", ".png");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int a = 0xFF;
                int r = random.nextInt(256);
                int g = random.nextInt(256);
                int b = random.nextInt(256);
                image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        ImageIO.write(image, "png", path.toFile());
        return path;
    }
}
