package io.github.zeroone3010.pngfilteropt.png;

import io.github.zeroone3010.pngfilteropt.filter.PngFilter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class FilterVisualizationWriter {
    public static final int DEFAULT_MAX_SIDE = 256;

    private static final Map<PngFilter, int[]> TINTS = Map.of(
            PngFilter.NONE, new int[] {255, 40, 40},
            PngFilter.SUB, new int[] {255, 170, 0},
            PngFilter.UP, new int[] {40, 110, 255},
            PngFilter.AVERAGE, new int[] {80, 210, 80},
            PngFilter.PAETH, new int[] {180, 80, 255}
    );

    private final int maxSide;

    public FilterVisualizationWriter(int maxSide) {
        if (maxSide < 1) {
            throw new IllegalArgumentException("maxSide must be at least 1");
        }
        this.maxSide = maxSide;
    }

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
        int outWidth = Math.max(1, scaledDimension(source.getWidth(), source.getHeight(), source.getWidth()));
        int proportionalHeight = Math.max(1, scaledDimension(source.getWidth(), source.getHeight(), source.getHeight()));
        int runPreservingHeight = Math.min(Math.min(source.getHeight(), maxSide), Math.max(1, layout.runs().size()));
        int outHeight = Math.max(proportionalHeight, runPreservingHeight);
        int[] sourceRows = representativeRows(layout, outHeight);
        BufferedImage rgb = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < outHeight; y++) {
            int sourceY = sourceRows[y];
            int tintRow = Math.min(sourceY, layout.rowFilters().size() - 1);
            int[] tint = TINTS.get(layout.rowFilters().get(tintRow));
            for (int x = 0; x < outWidth; x++) {
                int sourceX = Math.min(source.getWidth() - 1, (int) Math.floor((x + 0.5) * source.getWidth() / outWidth));
                int argb = source.getRGB(sourceX, sourceY);
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

    private int scaledDimension(int sourceWidth, int sourceHeight, int dimension) {
        int larger = Math.max(sourceWidth, sourceHeight);
        if (larger <= maxSide) return dimension;
        return Math.max(1, (int) Math.round((double) dimension * maxSide / larger));
    }

    private static int blend(int original, int tint) {
        return clamp((int) Math.round(original * 0.58 + tint * 0.42));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int[] representativeRows(FilterLayout layout, int outHeight) {
        int rowCount = layout.rowCount();
        if (rowCount == outHeight) {
            int[] rows = new int[outHeight];
            for (int i = 0; i < rows.length; i++) rows[i] = i;
            return rows;
        }
        if (layout.runs().size() <= outHeight && !layout.runs().isEmpty()) {
            return runPreservingRows(layout, outHeight);
        }
        int[] rows = new int[outHeight];
        for (int y = 0; y < outHeight; y++) {
            rows[y] = Math.min(rowCount - 1, (int) Math.floor((y + 0.5) * rowCount / outHeight));
        }
        return rows;
    }

    private static int[] runPreservingRows(FilterLayout layout, int outHeight) {
        List<Allocation> allocations = new ArrayList<>();
        int allocated = 0;
        for (int i = 0; i < layout.runs().size(); i++) {
            FilterLayout.Run run = layout.runs().get(i);
            double exact = (double) run.rowCount() * outHeight / layout.rowCount();
            int rows = Math.max(1, (int) Math.floor(exact));
            allocations.add(new Allocation(i, rows, exact - Math.floor(exact)));
            allocated += rows;
        }
        while (allocated < outHeight) {
            Allocation allocation = allocations.stream().max(Comparator.comparingDouble(a -> a.fraction)).orElseThrow();
            allocation.rows++;
            allocation.fraction = 0;
            allocated++;
        }
        while (allocated > outHeight) {
            Allocation allocation = allocations.stream()
                    .filter(a -> a.rows > 1)
                    .min(Comparator.comparingDouble(a -> a.fraction))
                    .orElseThrow();
            allocation.rows--;
            allocated--;
        }
        int[] rows = new int[outHeight];
        int y = 0;
        for (Allocation allocation : allocations) {
            FilterLayout.Run run = layout.runs().get(allocation.runIndex);
            for (int i = 0; i < allocation.rows; i++) {
                rows[y++] = Math.min(run.endRowInclusive(), run.startRow() + (int) Math.floor((i + 0.5) * run.rowCount() / allocation.rows));
            }
        }
        return rows;
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

    private static final class Allocation {
        final int runIndex;
        int rows;
        double fraction;

        Allocation(int runIndex, int rows, double fraction) {
            this.runIndex = runIndex;
            this.rows = rows;
            this.fraction = fraction;
        }
    }

    public record Visualization(Path path, long bytes, String dataUri) {
    }
}
