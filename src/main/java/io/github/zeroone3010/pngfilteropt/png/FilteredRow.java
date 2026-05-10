package io.github.zeroone3010.pngfilteropt.png;

import io.github.zeroone3010.pngfilteropt.filter.PngFilter;

public record FilteredRow(int rowIndex, PngFilter filter, byte[] filteredBytes) {
}
