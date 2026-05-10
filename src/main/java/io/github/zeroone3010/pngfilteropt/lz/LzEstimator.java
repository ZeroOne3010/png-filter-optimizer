package io.github.zeroone3010.pngfilteropt.lz;

public interface LzEstimator {
    int estimateCompressedCost(byte[] data);
}
