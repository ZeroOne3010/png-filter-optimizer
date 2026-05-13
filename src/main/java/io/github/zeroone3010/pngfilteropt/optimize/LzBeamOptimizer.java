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
        List<BeamState> states = new ArrayList<>();
        states.add(new BeamState(new ArrayList<>(image.height()), 0));

        for (int y = 0; y < image.height(); y++) {
            List<BeamState> expanded = new ArrayList<>();
            for (BeamState state : states) {
                var all = candidates.generateCandidates(image, y);
                if (all.isEmpty()) {
                    List<FilteredRow> path = new ArrayList<>(state.rows());
                    path.add(new FilteredRow(y, PngFilter.NONE, image.rows().get(y)));
                    expanded.add(new BeamState(path, state.score()));
                    continue;
                }
                for (FilteredRow row : all) {
                    List<FilteredRow> path = new ArrayList<>(state.rows());
                    path.add(row);
                    expanded.add(new BeamState(path, state.score() + score(row)));
                }
            }
            expanded.sort(Comparator.comparingInt(BeamState::score));
            states = expanded.subList(0, Math.min(beamWidth, expanded.size()));
        }

        BeamState best = states.stream().min(Comparator.comparingInt(BeamState::score)).orElseThrow();
        return new FilteredImage(image, best.rows());
    }

    private int score(FilteredRow row) {
        int score = 0;
        for (byte b : row.filteredBytes()) score += Math.abs((int) b);
        return score;
    }

    private record BeamState(List<FilteredRow> rows, int score) {}
}
