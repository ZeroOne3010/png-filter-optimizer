package io.github.zeroone3010.pngfilteropt.filter;

public interface RowFilter {
    PngFilter type();

    byte[] apply(byte[] row, byte[] previousRow, int bytesPerPixel);
}
