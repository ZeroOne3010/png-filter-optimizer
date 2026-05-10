package io.github.zeroone3010.pngfilteropt.png;

import java.util.List;

public record FilteredImage(int width, int height, List<FilteredRow> rows) {
}
