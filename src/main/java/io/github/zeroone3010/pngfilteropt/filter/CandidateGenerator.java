package io.github.zeroone3010.pngfilteropt.filter;

import java.util.List;

public class CandidateGenerator {
    public List<RowFilter> fixed(RowFilter filter) {
        return List.of(filter);
    }

    public List<RowFilter> allBaseline() {
        return List.of(new NoneFilter(), new SubFilter(), new UpFilter(), new AverageFilter(), new PaethFilter());
    }
}
