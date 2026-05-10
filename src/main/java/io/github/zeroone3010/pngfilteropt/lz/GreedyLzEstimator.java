package io.github.zeroone3010.pngfilteropt.lz;

public class GreedyLzEstimator implements LzEstimator {
    @Override
    public int estimateCost(byte[] data) {
        return data.length;
    }
}
