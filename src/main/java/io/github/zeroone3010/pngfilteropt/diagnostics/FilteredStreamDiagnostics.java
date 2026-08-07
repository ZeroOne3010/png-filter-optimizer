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
        ResidualDiagnostics residualDiagnostics,
        String filteredStreamFingerprint
) {
    /** Compatibility constructor for callers which do not have the exact stream available. */
    public FilteredStreamDiagnostics(int width, int height, int colorType, int bitDepth, int bytesPerPixel,
                                     int bytesPerRow, int streamLength, double entropy, int zeroByteCount,
                                     double zeroPercentage, int distinctByteValues, int longestIdenticalRun,
                                     int repeatedFullRowCount, int rowsEqualToPrevious, int mostCommonRowHashCount,
                                     FilterUsage filterUsage, RepetitionMetrics repetitionMetrics,
                                     LzParseDiagnostics lzParseDiagnostics, DirectionalSmoothness directionalSmoothness,
                                     ResidualDiagnostics residualDiagnostics) {
        this(width, height, colorType, bitDepth, bytesPerPixel, bytesPerRow, streamLength, entropy, zeroByteCount,
                zeroPercentage, distinctByteValues, longestIdenticalRun, repeatedFullRowCount, rowsEqualToPrevious,
                mostCommonRowHashCount, filterUsage, repetitionMetrics, lzParseDiagnostics, directionalSmoothness,
                residualDiagnostics, "");
    }

    public boolean isFilterEquivalentTo(FilteredStreamDiagnostics other) {
        return other != null && !filteredStreamFingerprint.isBlank()
                && filteredStreamFingerprint.equals(other.filteredStreamFingerprint);
    }
}
