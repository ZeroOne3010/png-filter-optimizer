package io.github.zeroone3010.pngfilteropt.filter;

import java.util.List;

public class CandidateGenerator {
    public List<RowFilter> fixed(RowFilter filter) {
        return List.of(filter);
    }
}
