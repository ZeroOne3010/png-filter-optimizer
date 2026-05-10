package io.github.zeroone3010.pngfilteropt.filter;

public final class AverageFilter implements RowFilter {
    @Override
    public PngFilter type() { return PngFilter.AVERAGE; }

    @Override
    public byte[] apply(byte[] currentRow, byte[] previousRow, int bytesPerPixel) {
        byte[] out = new byte[currentRow.length];
        for (int i = 0; i < currentRow.length; i++) {
            int left = i >= bytesPerPixel ? Byte.toUnsignedInt(currentRow[i - bytesPerPixel]) : 0;
            int up = previousRow == null ? 0 : Byte.toUnsignedInt(previousRow[i]);
            out[i] = (byte) (Byte.toUnsignedInt(currentRow[i]) - ((left + up) / 2));
        }
        return out;
    }
}
