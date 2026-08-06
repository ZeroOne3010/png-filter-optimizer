package io.github.zeroone3010.pngfilteropt.png;

import io.github.zeroone3010.pngfilteropt.filter.PngFilter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

public final class FilterVisualizationWriter {
    private static final Map<PngFilter, int[]> TINTS = Map.of(
            PngFilter.NONE, new int[] {255, 40, 40},
            PngFilter.SUB, new int[] {255, 170, 0},
            PngFilter.UP, new int[] {40, 110, 255},
            PngFilter.AVERAGE, new int[] {80, 210, 80},
            PngFilter.PAETH, new int[] {180, 80, 255}
    );

    public Visualization write(Path inputPng, FilterLayout layout, Path output) {
        try {
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            byte[] png = renderPngBytes(inputPng, layout);
            Files.write(output, png);
            return new Visualization(output, png.length, "data:image/png;base64," + Base64.getEncoder().encodeToString(png));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write filter visualization: " + output, e);
        }
    }

    public byte[] renderPngBytes(Path inputPng, FilterLayout layout) throws IOException {
        BufferedImage source = ImageIO.read(inputPng.toFile());
        if (source == null) {
            throw new IllegalArgumentException("Unsupported image for visualization: " + inputPng);
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            int sourceY = y;
            int tintRow = Math.min(sourceY, layout.rowFilters().size() - 1);
            int[] tint = TINTS.get(layout.rowFilters().get(tintRow));
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, sourceY);
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                int tr = blend(r, tint[0]);
                int tg = blend(g, tint[1]);
                int tb = blend(b, tint[2]);
                rgb.setRGB(x, y, (tr << 16) | (tg << 8) | tb);
            }
        }
        BufferedImage indexed = toIndexed(rgb);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(indexed, "png", out);
        return out.toByteArray();
    }

    private static int blend(int original, int tint) {
        return clamp((int) Math.round(original * 0.58 + tint * 0.42));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static BufferedImage toIndexed(BufferedImage rgb) {
        BufferedImage indexed = new BufferedImage(rgb.getWidth(), rgb.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, palette());
        indexed.getGraphics().drawImage(rgb, 0, 0, null);
        return indexed;
    }

    private static IndexColorModel palette() {
        byte[] r = new byte[256];
        byte[] g = new byte[256];
        byte[] b = new byte[256];
        int i = 0;
        int[] levels = {0, 51, 102, 153, 204, 255};
        for (int rr : levels) for (int gg : levels) for (int bb : levels) {
            r[i] = (byte) rr; g[i] = (byte) gg; b[i] = (byte) bb; i++;
        }
        for (int gray = 0; i < 256; i++, gray++) {
            int v = Math.round(gray * 255f / 39f);
            r[i] = (byte) v; g[i] = (byte) v; b[i] = (byte) v;
        }
        return new IndexColorModel(8, 256, r, g, b);
    }

    public record Visualization(Path path, long bytes, String dataUri) {
    }
}
