package io.github.zeroone3010.pngfilteropt.lz;

import java.util.List;

public final class HashChainLzHistory implements LzHistory {
    @Override public void reset() {}
    @Override public void addByte(byte value, int position) {}
    @Override public List<Match> findMatches(byte[] data, int position, int maxLength) { return List.of(); }
}
