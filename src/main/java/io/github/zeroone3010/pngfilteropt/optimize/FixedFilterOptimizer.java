package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.RowFilter;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.FilteredRow;
import io.github.zeroone3010.pngfilteropt.png.RawImage;
import java.util.ArrayList;
import java.util.List;

public class FixedFilterOptimizer implements FilterOptimizer {
    private final RowFilter filter;

    public FixedFilterOptimizer(RowFilter filter) {
        this.filter = filter;
    }

    @Override
    public FilteredImage optimize(RawImage rawImage) {
        List<FilteredRow> rows = new ArrayList<>(rawImage.rows().size());
        byte[] previous = null;
        for (byte[] row : rawImage.rows()) {
            rows.add(new FilteredRow(filter.type(), filter.apply(row, previous, rawImage.bytesPerPixel())));
            previous = row;
        }
        return new FilteredImage(rawImage.width(), rawImage.height(), rows);
    }
}
