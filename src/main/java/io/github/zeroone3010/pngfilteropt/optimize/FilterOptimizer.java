package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.RawImage;

public interface FilterOptimizer {
    FilteredImage optimize(RawImage rawImage);
}
