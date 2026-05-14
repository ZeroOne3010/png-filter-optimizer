package io.github.zeroone3010.pngfilteropt.diagnostics;

import io.github.zeroone3010.pngfilteropt.png.RawImage;

public final class DirectionalityAnalyzer {
    public DirectionalSmoothness directionalSmoothness(RawImage image) {
        int bpp = Math.max(1, image.bytesPerPixel());
        long hAbs = 0;
        long vAbs = 0;
        long hCount = 0;
        long vCount = 0;
        for (int y = 0; y < image.rows().size(); y++) {
            byte[] row = image.rows().get(y);
            byte[] prev = y > 0 ? image.rows().get(y - 1) : null;
            for (int x = 0; x < row.length; x++) {
                int current = Byte.toUnsignedInt(row[x]);
                if (x >= bpp) {
                    hAbs += Math.abs(current - Byte.toUnsignedInt(row[x - bpp]));
                    hCount++;
                }
                if (prev != null) {
                    vAbs += Math.abs(current - Byte.toUnsignedInt(prev[x]));
                    vCount++;
                }
            }
        }
        double meanH = hCount == 0 ? 0d : (double) hAbs / hCount;
        double meanV = vCount == 0 ? 0d : (double) vAbs / vCount;
        double ratio = meanH == 0d ? (meanV == 0d ? 1d : Double.POSITIVE_INFINITY) : meanV / meanH;
        return new DirectionalSmoothness(meanH, meanV, ratio);
    }

    public ResidualDiagnostics residualDiagnostics(RawImage image) {
        int bpp = Math.max(1, image.bytesPerPixel());
        long none = 0, sub = 0, up = 0, avg = 0, paeth = 0;
        for (int y = 0; y < image.rows().size(); y++) {
            byte[] row = image.rows().get(y);
            byte[] prev = y > 0 ? image.rows().get(y - 1) : null;
            for (int x = 0; x < row.length; x++) {
                int raw = Byte.toUnsignedInt(row[x]);
                int left = x >= bpp ? Byte.toUnsignedInt(row[x - bpp]) : 0;
                int upv = prev == null ? 0 : Byte.toUnsignedInt(prev[x]);
                int upLeft = prev != null && x >= bpp ? Byte.toUnsignedInt(prev[x - bpp]) : 0;
                none += absSignedByte(raw);
                sub += absSignedByte(raw - left);
                up += absSignedByte(raw - upv);
                avg += absSignedByte(raw - ((left + upv) / 2));
                paeth += absSignedByte(raw - paethPredictor(left, upv, upLeft));
            }
        }
        return new ResidualDiagnostics(none, sub, up, avg, paeth);
    }

    private static int absSignedByte(int rawResidual) {
        return Math.abs((byte) rawResidual);
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
