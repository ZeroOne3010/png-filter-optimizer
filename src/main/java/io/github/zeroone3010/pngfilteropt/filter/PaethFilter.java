package io.github.zeroone3010.pngfilteropt.filter;

public class PaethFilter implements RowFilter {
    @Override
    public PngFilter type() {
        return PngFilter.PAETH;
    }

    @Override
    public byte[] apply(byte[] row, byte[] previousRow, int bytesPerPixel) {
        byte[] out = new byte[row.length];
        for (int i = 0; i < row.length; i++) {
            int left = i >= bytesPerPixel ? row[i - bytesPerPixel] & 0xFF : 0;
            int up = previousRow != null ? previousRow[i] & 0xFF : 0;
            int upLeft = previousRow != null && i >= bytesPerPixel ? previousRow[i - bytesPerPixel] & 0xFF : 0;
            out[i] = (byte) ((row[i] & 0xFF) - paethPredictor(left, up, upLeft));
        }
        return out;
    }

    private int paethPredictor(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) return a;
        if (pb <= pc) return b;
        return c;
    }
}
