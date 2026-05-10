package io.github.zeroone3010.pngfilteropt.filter;

import java.util.Arrays;

public class NoneFilter implements RowFilter {
    @Override
    public PngFilter type() {
        return PngFilter.NONE;
    }

    @Override
    public byte[] apply(byte[] row, byte[] previousRow, int bytesPerPixel) {
        return Arrays.copyOf(row, row.length);
    }
}
