package io.github.zeroone3010.pngfilteropt.diagnostics;

import io.github.zeroone3010.pngfilteropt.png.RawImage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectionalityAnalyzerTest {
    private final DirectionalityAnalyzer analyzer = new DirectionalityAnalyzer();

    @Test void horizontalGradientIsSubFriendly() {
        RawImage image = rgbGradient(16, 16, true);
        var d = analyzer.directionalSmoothness(image);
        assertTrue(d.verticalHorizontalRatio() < 0.7);
    }

    @Test void verticalGradientIsUpFriendly() {
        RawImage image = rgbGradient(16, 16, false);
        var d = analyzer.directionalSmoothness(image);
        assertTrue(d.verticalHorizontalRatio() > 1.5);
    }

    @Test void flatColorHasNearZeroPredictiveResiduals() {
        RawImage image = solidGray(16, 16, (byte) 12);
        var residual = analyzer.residualDiagnostics(image);
        assertTrue(residual.subSumAbs() < residual.noneSumAbs());
        assertTrue(residual.upSumAbs() < residual.noneSumAbs());
        assertTrue(residual.paethSumAbs() < residual.noneSumAbs());
    }

    @Test void randomNoiseIsHighInAllDirections() {
        RawImage image = randomGray(32, 32, 1234);
        var d = analyzer.directionalSmoothness(image);
        var r = analyzer.residualDiagnostics(image);
        assertTrue(d.meanHorizontalDelta() > 40);
        assertTrue(d.meanVerticalDelta() > 40);
        assertTrue(r.noneSumAbs() > 0 && r.subSumAbs() > 0 && r.upSumAbs() > 0);
    }

    @Test void checkerboardHasStrongResiduals() {
        RawImage image = checkerboardGray(16, 16);
        var r = analyzer.residualDiagnostics(image);
        assertTrue(r.subSumAbs() > 0);
        assertTrue(r.upSumAbs() > 0);
    }

    private static RawImage rgbGradient(int w, int h, boolean horizontal) {
        List<byte[]> rows = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            byte[] row = new byte[w * 3];
            for (int x = 0; x < w; x++) {
                int v = horizontal ? x * 8 : y * 8;
                row[x * 3] = (byte) v;
                row[x * 3 + 1] = (byte) v;
                row[x * 3 + 2] = (byte) v;
            }
            rows.add(row);
        }
        return new RawImage(w, h, 8, 2, 3, w * 3, rows);
    }

    private static RawImage solidGray(int w, int h, byte value) {
        List<byte[]> rows = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            byte[] row = new byte[w];
            java.util.Arrays.fill(row, value);
            rows.add(row);
        }
        return new RawImage(w, h, 8, 0, 1, w, rows);
    }

    private static RawImage randomGray(int w, int h, long seed) {
        Random random = new Random(seed);
        List<byte[]> rows = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            byte[] row = new byte[w];
            random.nextBytes(row);
            rows.add(row);
        }
        return new RawImage(w, h, 8, 0, 1, w, rows);
    }

    private static RawImage checkerboardGray(int w, int h) {
        List<byte[]> rows = new ArrayList<>();
        for (int y = 0; y < h; y++) {
            byte[] row = new byte[w];
            for (int x = 0; x < w; x++) row[x] = (byte) (((x + y) & 1) == 0 ? 0 : 255);
            rows.add(row);
        }
        return new RawImage(w, h, 8, 0, 1, w, rows);
    }
}
