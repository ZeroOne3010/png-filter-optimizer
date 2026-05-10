package io.github.zeroone3010.pngfilteropt.lz;

public class HashChainLzHistory implements LzHistory {
    @Override
    public void put(byte[] data, int index) {
        // Milestone stub
    }

    @Override
    public Match findBest(byte[] data, int index) {
        return new Match(0, 0);
    }
}
