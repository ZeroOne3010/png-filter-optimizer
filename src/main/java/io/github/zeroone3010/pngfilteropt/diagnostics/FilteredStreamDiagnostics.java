package io.github.zeroone3010.pngfilteropt.diagnostics;

public record FilteredStreamDiagnostics(
        int width,
        int height,
        int colorType,
        int bitDepth,
        int bytesPerPixel,
        int bytesPerRow,
        int streamLength,
        double entropy,
        int zeroByteCount,
        double zeroPercentage,
        int distinctByteValues,
        int longestIdenticalRun,
        int repeatedFullRowCount,
        int rowsEqualToPrevious,
        int mostCommonRowHashCount,
        FilterUsage filterUsage,
        RepetitionMetrics repetitionMetrics,
        LzParseDiagnostics lzParseDiagnostics,
        DirectionalSmoothness directionalSmoothness,
        ResidualDiagnostics residualDiagnostics
) {
}
