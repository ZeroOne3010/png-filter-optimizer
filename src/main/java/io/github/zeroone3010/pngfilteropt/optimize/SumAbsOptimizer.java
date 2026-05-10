package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.NoneFilter;

public class SumAbsOptimizer extends FixedFilterOptimizer {
    public SumAbsOptimizer() {
        super(new NoneFilter());
    }
}
