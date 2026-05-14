package io.github.zeroone3010.pngfilteropt.diagnostics;

public record ResidualDiagnostics(
        long noneSumAbs,
        long subSumAbs,
        long upSumAbs,
        long averageSumAbs,
        long paethSumAbs
) {
}
