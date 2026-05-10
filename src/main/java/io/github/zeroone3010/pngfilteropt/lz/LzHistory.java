package io.github.zeroone3010.pngfilteropt.lz;

import java.util.List;

public interface LzHistory {
    void reset();

    void addByte(byte value, int position);

    List<Match> findMatches(byte[] data, int position, int maxLength);
}
