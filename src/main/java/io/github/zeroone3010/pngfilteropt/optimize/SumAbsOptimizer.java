package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.FilteredRow;
import io.github.zeroone3010.pngfilteropt.png.RawImage;

import java.util.ArrayList;
import java.util.List;

public final class SumAbsOptimizer implements FilterOptimizer {
    @Override
    public String name() { return "sumabs"; }

    @Override
    public FilteredImage optimize(RawImage image, CandidateGenerator candidates) {
        List<FilteredRow> rows = new ArrayList<>(image.height());
        for (int y = 0; y < image.height(); y++) {
            var all = candidates.generateCandidates(image, y);
            FilteredRow best = new FilteredRow(y, PngFilter.NONE, image.rows().get(y));
            int bestScore = Integer.MAX_VALUE;
            for (FilteredRow candidate : all) {
                int candidateScore = score(candidate);
                if (candidateScore < bestScore || (candidateScore == bestScore
                        && candidate.filter().pngValue() < best.filter().pngValue())) {
                    best = candidate;
                    bestScore = candidateScore;
                }
            }
            rows.add(best);
        }
        return new FilteredImage(image, rows);
    }

    private int score(FilteredRow row) {
        int score = 0;
        for (byte b : row.filteredBytes()) {
            int unsigned = Byte.toUnsignedInt(b);
            score += Math.min(unsigned, 256 - unsigned);
        }
        return score;
    }
}
