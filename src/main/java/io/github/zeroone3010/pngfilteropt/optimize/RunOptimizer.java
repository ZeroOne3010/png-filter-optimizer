package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.FilteredRow;
import io.github.zeroone3010.pngfilteropt.png.RawImage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RunOptimizer implements FilterOptimizer {
    @Override
    public String name() { return "run"; }

    @Override
    public FilteredImage optimize(RawImage image, CandidateGenerator candidates) {
        List<FilteredRow> rows = new ArrayList<>(image.height());
        Comparator<FilteredRow> byRunScore = Comparator
                .comparingInt(this::score)
                .thenComparingInt(row -> row.filter().pngValue());

        for (int y = 0; y < image.height(); y++) {
            var all = candidates.generateCandidates(image, y);
            rows.add(all.stream().min(byRunScore).orElse(new FilteredRow(y, PngFilter.NONE, image.rows().get(y))));
        }
        return new FilteredImage(image, rows);
    }

    private int score(FilteredRow row) {
        byte[] bytes = row.filteredBytes();
        if (bytes.length == 0) return 0;

        int score = 0;
        int runLength = 1;

        for (int i = 1; i < bytes.length; i++) {
            if (bytes[i] == bytes[i - 1]) {
                runLength++;
                continue;
            }
            score += runPenalty(runLength);
            runLength = 1;
        }
        score += runPenalty(runLength);
        return score;
    }

    private int runPenalty(int runLength) {
        // Lower score is better.
        // DEFLATE tends to favor longer repeated byte sequences, so we penalize short runs
        // and quickly discount longer runs:
        // length 1 -> +16
        // length 2 -> +8
        // length 3 -> +4
        // length 4+ -> +1
        // This makes many short runs expensive while long runs remain cheap.
        return runLength >= 4 ? 1 : 1 << (5 - runLength);
    }
}
