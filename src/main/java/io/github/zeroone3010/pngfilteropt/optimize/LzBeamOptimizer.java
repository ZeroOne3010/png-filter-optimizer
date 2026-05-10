package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.FilteredRow;
import io.github.zeroone3010.pngfilteropt.png.RawImage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LzBeamOptimizer implements FilterOptimizer {
    @Override
    public String name() { return "lzbeam"; }

    @Override
    public FilteredImage optimize(RawImage image, CandidateGenerator candidates) {
        List<FilteredRow> rows = new ArrayList<>(image.height());
        for (int y = 0; y < image.height(); y++) {
            var all = candidates.generateCandidates(image, y);
            rows.add(all.stream().min(Comparator.comparingInt(this::score)).orElse(new FilteredRow(y, PngFilter.NONE, image.rows().get(y))));
        }
        return new FilteredImage(image, rows);
    }

    private int score(FilteredRow row) {
        int score = 0;
        for (byte b : row.filteredBytes()) score += Math.abs((int) b);
        return score;
    }
}
