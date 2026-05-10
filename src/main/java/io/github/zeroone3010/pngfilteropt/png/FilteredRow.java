package io.github.zeroone3010.pngfilteropt.png;

import io.github.zeroone3010.pngfilteropt.filter.PngFilter;

public record FilteredRow(PngFilter filter, byte[] data) {
}
