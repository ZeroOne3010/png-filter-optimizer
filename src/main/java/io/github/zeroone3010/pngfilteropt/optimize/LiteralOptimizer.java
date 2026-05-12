package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.RawImage;

public final class LiteralOptimizer implements FilterOptimizer {
    private final FixedFilterOptimizer delegate = new FixedFilterOptimizer(PngFilter.NONE);

    @Override
    public String name() {
        return "literal";
    }

    @Override
    public FilteredImage optimize(RawImage image, CandidateGenerator candidates) {
        return delegate.optimize(image, candidates);
    }
}
