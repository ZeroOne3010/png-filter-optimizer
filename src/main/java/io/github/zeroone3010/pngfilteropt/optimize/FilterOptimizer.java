package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.RawImage;

public interface FilterOptimizer {
    String name();

    FilteredImage optimize(RawImage image, CandidateGenerator candidates);
}
