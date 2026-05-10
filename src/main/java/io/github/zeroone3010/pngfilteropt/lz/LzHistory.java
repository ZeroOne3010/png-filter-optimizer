package io.github.zeroone3010.pngfilteropt.lz;

public interface LzHistory {
    void put(byte[] data, int index);

    Match findBest(byte[] data, int index);
}
