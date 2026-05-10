package io.github.zeroone3010.pngfilteropt.filter;

import io.github.zeroone3010.pngfilteropt.png.FilteredRow;
import io.github.zeroone3010.pngfilteropt.png.RawImage;

import java.util.ArrayList;
import java.util.List;

public final class CandidateGenerator {
    private final List<RowFilter> filters = List.of(
            new NoneFilter(), new SubFilter(), new UpFilter(), new AverageFilter(), new PaethFilter()
    );

    public List<FilteredRow> generateCandidates(RawImage image, int rowIndex) {
        byte[] current = image.rows().get(rowIndex);
        byte[] previous = rowIndex > 0 ? image.rows().get(rowIndex - 1) : null;
        List<FilteredRow> result = new ArrayList<>(filters.size());
        for (RowFilter filter : filters) {
            result.add(new FilteredRow(rowIndex, filter.type(), filter.apply(current, previous, image.bytesPerPixel())));
        }
        return result;
    }
}
