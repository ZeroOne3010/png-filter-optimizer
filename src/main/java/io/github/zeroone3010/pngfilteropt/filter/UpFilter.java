package io.github.zeroone3010.pngfilteropt.filter;

public class UpFilter implements RowFilter {
    @Override
    public PngFilter type() {
        return PngFilter.UP;
    }

    @Override
    public byte[] apply(byte[] row, byte[] previousRow, int bytesPerPixel) {
        byte[] out = new byte[row.length];
        for (int i = 0; i < row.length; i++) {
            int up = previousRow != null ? previousRow[i] & 0xFF : 0;
            out[i] = (byte) ((row[i] & 0xFF) - up);
        }
        return out;
    }
}
