package io.github.zeroone3010.pngfilteropt.png;

import java.util.List;

public record RawImage(
        int width,
        int height,
        int bitDepth,
        int colorType,
        int bytesPerPixel,
        int bytesPerRow,
        List<byte[]> rows,
        byte[] paletteRgb,
        byte[] transparencyAlpha
) {
    public RawImage(int width, int height, int bitDepth, int colorType, int bytesPerPixel, int bytesPerRow, List<byte[]> rows) {
        this(width, height, bitDepth, colorType, bytesPerPixel, bytesPerRow, rows, null, null);
    }
}
