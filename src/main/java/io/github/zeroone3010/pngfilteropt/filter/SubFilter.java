package io.github.zeroone3010.pngfilteropt.filter;

public final class SubFilter implements RowFilter {
    @Override
    public PngFilter type() { return PngFilter.SUB; }

    @Override
    public byte[] apply(byte[] currentRow, byte[] previousRow, int bytesPerPixel) {
        byte[] out = new byte[currentRow.length];
        for (int i = 0; i < currentRow.length; i++) {
            int left = i >= bytesPerPixel ? Byte.toUnsignedInt(currentRow[i - bytesPerPixel]) : 0;
            out[i] = (byte) (Byte.toUnsignedInt(currentRow[i]) - left);
        }
        return out;
    }
}
