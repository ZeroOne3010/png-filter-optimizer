package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.FilteredRow;
import io.github.zeroone3010.pngfilteropt.png.RawImage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

public final class HierarchicalSplitOptimizer implements FilterOptimizer {
    private static final List<PngFilter> FILTERS = List.of(PngFilter.NONE, PngFilter.SUB, PngFilter.UP, PngFilter.AVERAGE, PngFilter.PAETH);

    private final int maxDepth;
    private final int minSegmentRows;

    public HierarchicalSplitOptimizer(int maxDepth, int minSegmentRows) {
        this.maxDepth = Math.max(0, maxDepth);
        this.minSegmentRows = Math.max(1, minSegmentRows);
    }

    @Override
    public String name() { return "hierarchical"; }

    @Override
    public FilteredImage optimize(RawImage image, CandidateGenerator candidates) {
        List<Map<PngFilter, FilteredRow>> byRowAndFilter = precomputeRows(image, candidates);
        List<FilteredRow> incumbent = bestGlobalSeed(image, byRowAndFilter);
        long incumbentScore = scoreImage(image, incumbent);

        ArrayDeque<Segment> segments = new ArrayDeque<>();
        segments.add(new Segment(0, image.height(), 0));

        while (!segments.isEmpty()) {
            int segmentCount = segments.size();
            boolean improved = false;
            for (int i = 0; i < segmentCount; i++) {
                Segment segment = segments.removeFirst();
                if (segment.depth() >= maxDepth || segment.length() < minSegmentRows) {
                    continue;
                }

                Candidate best = bestSegmentCandidate(image, incumbent, byRowAndFilter, segment, incumbentScore);
                if (best.score() < incumbentScore) {
                    incumbent = best.rows();
                    incumbentScore = best.score();
                    improved = true;
                }

                if (segment.length() > minSegmentRows) {
                    int mid = segment.start() + (segment.length() / 2);
                    segments.addLast(new Segment(segment.start(), mid, segment.depth() + 1));
                    segments.addLast(new Segment(mid, segment.end(), segment.depth() + 1));
                }
            }
            if (!improved) {
                break;
            }
        }

        return new FilteredImage(image, incumbent);
    }

    private static Candidate bestSegmentCandidate(RawImage image, List<FilteredRow> incumbent, List<Map<PngFilter, FilteredRow>> byRowAndFilter, Segment segment, long incumbentScore) {
        Candidate best = new Candidate(incumbent, incumbentScore);
        for (PngFilter filter : FILTERS) {
            List<FilteredRow> trial = new ArrayList<>(incumbent);
            for (int y = segment.start(); y < segment.end(); y++) {
                trial.set(y, byRowAndFilter.get(y).get(filter));
            }
            long score = scoreImage(image, trial);
            if (score < best.score()) {
                best = new Candidate(trial, score);
            }
        }
        return best;
    }

    private static List<FilteredRow> bestGlobalSeed(RawImage image, List<Map<PngFilter, FilteredRow>> byRowAndFilter) {
        Candidate best = null;
        for (PngFilter filter : FILTERS) {
            List<FilteredRow> rows = new ArrayList<>(image.height());
            for (int y = 0; y < image.height(); y++) rows.add(byRowAndFilter.get(y).get(filter));
            long score = scoreImage(image, rows);
            if (best == null || score < best.score()) {
                best = new Candidate(rows, score);
            }
        }
        return best.rows();
    }

    private static List<Map<PngFilter, FilteredRow>> precomputeRows(RawImage image, CandidateGenerator candidates) {
        List<Map<PngFilter, FilteredRow>> out = new ArrayList<>(image.height());
        for (int y = 0; y < image.height(); y++) {
            EnumMap<PngFilter, FilteredRow> map = new EnumMap<>(PngFilter.class);
            for (FilteredRow row : candidates.generateCandidates(image, y)) map.put(row.filter(), row);
            out.add(map);
        }
        return out;
    }

    private static long scoreImage(RawImage image, List<FilteredRow> rows) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            for (int y = 0; y < image.height(); y++) {
                FilteredRow row = rows.get(y);
                raw.write(row.filter().pngValue());
                raw.write(row.filteredBytes());
            }
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
                deflater.write(raw.toByteArray());
            }
            return compressed.size();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Segment(int start, int end, int depth) {
        int length() { return end - start; }
    }

    private record Candidate(List<FilteredRow> rows, long score) {}
}
