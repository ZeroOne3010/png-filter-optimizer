package io.github.zeroone3010.pngfilteropt.filter;

public final class PaethFilter implements RowFilter {
    @Override
    public PngFilter type() { return PngFilter.PAETH; }

    @Override
    public byte[] apply(byte[] currentRow, byte[] previousRow, int bytesPerPixel) {
        byte[] out = new byte[currentRow.length];
        for (int i = 0; i < currentRow.length; i++) {
            int a = i >= bytesPerPixel ? Byte.toUnsignedInt(currentRow[i - bytesPerPixel]) : 0;
            int b = previousRow == null ? 0 : Byte.toUnsignedInt(previousRow[i]);
            int c = previousRow != null && i >= bytesPerPixel ? Byte.toUnsignedInt(previousRow[i - bytesPerPixel]) : 0;
            out[i] = (byte) (Byte.toUnsignedInt(currentRow[i]) - paethPredictor(a, b, c));
        }
        return out;
    }

    private static int paethPredictor(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) return a;
        if (pb <= pc) return b;
        return c;
    }
}
