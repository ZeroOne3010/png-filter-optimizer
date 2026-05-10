package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.NoneFilter;

public class RunOptimizer extends FixedFilterOptimizer {
    public RunOptimizer() {
        super(new NoneFilter());
    }
}
