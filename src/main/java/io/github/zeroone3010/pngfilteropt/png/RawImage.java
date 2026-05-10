package io.github.zeroone3010.pngfilteropt.png;

import java.util.List;

public record RawImage(int width, int height, int bytesPerPixel, List<byte[]> rows) {
    public RawImage {
        if (width < 0 || height < 0 || bytesPerPixel <= 0) {
            throw new IllegalArgumentException("Invalid image dimensions or pixel depth");
        }
    }
}
