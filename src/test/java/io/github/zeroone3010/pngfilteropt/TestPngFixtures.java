package io.github.zeroone3010.pngfilteropt;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class TestPngFixtures {
    private TestPngFixtures() {}

    static Path createPng(Path output, int width, int height) {
        try {
            Files.createDirectories(output.getParent());
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setRGB(x, y, ((x + y) % 2 == 0) ? 0xFF336699 : 0xFFCC8844);
                }
            }
            ImageIO.write(image, "png", output.toFile());
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Failed creating test PNG fixture: " + output, e);
        }
    }
}
