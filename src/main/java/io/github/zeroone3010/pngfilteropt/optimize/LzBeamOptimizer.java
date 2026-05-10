package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.NoneFilter;

public class LzBeamOptimizer extends FixedFilterOptimizer {
    public LzBeamOptimizer() {
        super(new NoneFilter());
    }
}
