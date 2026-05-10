package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.FilteredRow;
import io.github.zeroone3010.pngfilteropt.png.RawImage;

import java.util.ArrayList;
import java.util.List;

public final class FixedFilterOptimizer implements FilterOptimizer {
    private final PngFilter filter;

    public FixedFilterOptimizer(PngFilter filter) { this.filter = filter; }

    @Override
    public String name() { return "fixed-" + filter.name().toLowerCase(); }

    @Override
    public FilteredImage optimize(RawImage image, CandidateGenerator candidates) {
        List<FilteredRow> rows = new ArrayList<>(image.height());
        for (int y = 0; y < image.height(); y++) {
            var candidate = candidates.generateCandidates(image, y).stream().filter(c -> c.filter() == filter).findFirst().orElseThrow();
            rows.add(candidate);
        }
        return new FilteredImage(image, rows);
    }
}
