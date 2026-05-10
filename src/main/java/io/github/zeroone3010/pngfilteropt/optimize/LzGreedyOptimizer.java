package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.NoneFilter;

public class LzGreedyOptimizer extends FixedFilterOptimizer {
    public LzGreedyOptimizer() {
        super(new NoneFilter());
    }
}
