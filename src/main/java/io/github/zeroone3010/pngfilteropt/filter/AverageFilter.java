package io.github.zeroone3010.pngfilteropt.filter;

public class AverageFilter implements RowFilter {
    @Override
    public PngFilter type() {
        return PngFilter.AVERAGE;
    }

    @Override
    public byte[] apply(byte[] row, byte[] previousRow, int bytesPerPixel) {
        byte[] out = new byte[row.length];
        for (int i = 0; i < row.length; i++) {
            int left = i >= bytesPerPixel ? row[i - bytesPerPixel] & 0xFF : 0;
            int up = previousRow != null ? previousRow[i] & 0xFF : 0;
            out[i] = (byte) ((row[i] & 0xFF) - ((left + up) >>> 1));
        }
        return out;
    }
}
