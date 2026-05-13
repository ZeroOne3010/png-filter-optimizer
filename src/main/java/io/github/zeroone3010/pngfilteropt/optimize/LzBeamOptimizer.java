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
    private final int beamWidth;

    public LzBeamOptimizer(int beamWidth) {
        this.beamWidth = Math.max(1, beamWidth);
    }

    @Override
    public String name() { return "lzbeam"; }

    @Override
    public FilteredImage optimize(RawImage image, CandidateGenerator candidates) {
        List<BeamState> beam = List.of(new BeamState(null, null, 0));

        for (int y = 0; y < image.height(); y++) {
            List<BeamState> expanded = new ArrayList<>();
            List<FilteredRow> rowCandidates = candidates.generateCandidates(image, y);
            for (BeamState state : beam) {
                for (FilteredRow row : rowCandidates) {
                    expanded.add(new BeamState(state, row, state.totalScore + score(row)));
                }
            }
            beam = expanded.stream()
                    .sorted(Comparator.comparingInt(BeamState::totalScore))
                    .limit(beamWidth)
                    .toList();
        }

        BeamState best = beam.stream()
                .min(Comparator.comparingInt(BeamState::totalScore))
                .orElseThrow(() -> new IllegalStateException("Beam search produced no candidates"));

        List<FilteredRow> rows = new ArrayList<>(image.height());
        BeamState cursor = best;
        while (cursor != null && cursor.row != null) {
            rows.add(cursor.row);
            cursor = cursor.previous;
        }
        java.util.Collections.reverse(rows);

        while (rows.size() < image.height()) {
            int y = rows.size();
            rows.add(new FilteredRow(y, PngFilter.NONE, image.rows().get(y)));
        }

        return new FilteredImage(image, rows);
    }

    private int score(FilteredRow row) {
        int score = 0;
        for (byte b : row.filteredBytes()) score += Math.abs((int) b);
        return score;
    }

    private record BeamState(BeamState previous, FilteredRow row, int totalScore) {
    }
}
