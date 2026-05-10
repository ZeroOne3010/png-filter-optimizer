package io.github.zeroone3010.pngfilteropt.lz;

public final class GreedyLzEstimator implements LzEstimator {
    private final LzHistory history;

    public GreedyLzEstimator(LzHistory history) {
        this.history = history;
    }

    @Override
    public int estimateCompressedCost(byte[] data) {
        history.reset();
        return data.length;
    }
}
