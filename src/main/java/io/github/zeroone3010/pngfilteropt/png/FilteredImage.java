package io.github.zeroone3010.pngfilteropt.png;

import java.util.List;

public record FilteredImage(RawImage source, List<FilteredRow> rows) {
}
