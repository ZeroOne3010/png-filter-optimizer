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
            List<FilteredRow> rowCandidates = candidates.generateCandidates(image, y);
            List<BeamState> expanded = new ArrayList<>();
            for (BeamState state : states) {
                if (rowCandidates.isEmpty()) {
                    List<FilteredRow> path = new ArrayList<>(state.rows());
                    FilteredRow fallback = new FilteredRow(y, PngFilter.NONE, image.rows().get(y));
                    path.add(fallback);
                    expanded.add(new BeamState(path, state.score() + transitionScore(state.lastRow(), fallback)));
                    continue;
                }
                for (FilteredRow row : rowCandidates) {
                    List<FilteredRow> path = new ArrayList<>(state.rows());
                    path.add(row);
                    expanded.add(new BeamState(path, state.score() + transitionScore(state.lastRow(), row)));
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

    private int transitionScore(FilteredRow previous, FilteredRow current) {
        int base = score(current);
        if (previous == null) {
            return base;
        }

        byte[] prev = previous.filteredBytes();
        byte[] curr = current.filteredBytes();
        int alignedMatches = 0;
        int minLen = Math.min(prev.length, curr.length);
        for (int i = 0; i < minLen; i++) {
            if (prev[i] == curr[i]) alignedMatches++;
        }

        int boundaryMatches = 0;
        int window = Math.min(32, minLen);
        for (int i = 1; i <= window; i++) {
            if (prev[prev.length - i] != curr[window - i]) break;
            boundaryMatches++;
        }

        return base - (alignedMatches / 8) - (boundaryMatches * 2);
    }

    private record BeamState(List<FilteredRow> rows, int score) {
        private FilteredRow lastRow() {
            return rows.isEmpty() ? null : rows.get(rows.size() - 1);
        }
    }
}
