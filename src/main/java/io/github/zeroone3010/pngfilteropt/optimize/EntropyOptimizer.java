package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.NoneFilter;

public class EntropyOptimizer extends FixedFilterOptimizer {
    public EntropyOptimizer() {
        super(new NoneFilter());
    }
}
