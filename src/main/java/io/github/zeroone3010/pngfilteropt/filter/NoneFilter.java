package io.github.zeroone3010.pngfilteropt.filter;

import java.util.Arrays;

public final class NoneFilter implements RowFilter {
    @Override
    public PngFilter type() { return PngFilter.NONE; }

    @Override
    public byte[] apply(byte[] currentRow, byte[] previousRow, int bytesPerPixel) {
        return Arrays.copyOf(currentRow, currentRow.length);
    }
}
