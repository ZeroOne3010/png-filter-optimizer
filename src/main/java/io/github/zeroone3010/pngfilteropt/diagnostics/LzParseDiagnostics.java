package io.github.zeroone3010.pngfilteropt.diagnostics;

public record LzParseDiagnostics(
        long literalTokenCount,
        long matchTokenCount,
        long matchedByteCount,
        long literalByteCount,
        double matchCoveragePercent,
        double averageMatchLength,
        int maxMatchLength,
        long[] matchLengthBuckets,
        long[] matchDistanceBuckets,
        long approximateLzCostBits,
        double literalByteEntropy,
        double lengthSymbolEntropy,
        double distanceSymbolEntropy
) {
}
