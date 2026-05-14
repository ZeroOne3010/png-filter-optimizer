package io.github.zeroone3010.pngfilteropt.diagnostics;

public record DirectionalSmoothness(
        double meanHorizontalDelta,
        double meanVerticalDelta,
        double verticalHorizontalRatio
) {
}
