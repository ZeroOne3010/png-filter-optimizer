package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.diagnostics.DiagnosticsCalculator;
import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.FilteredRow;
import io.github.zeroone3010.pngfilteropt.png.RawImage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class HierarchicalSplitOptimizer implements FilterOptimizer {
    private static final List<PngFilter> FIXED_FILTERS = List.of(PngFilter.NONE, PngFilter.SUB, PngFilter.UP, PngFilter.AVERAGE, PngFilter.PAETH);

    private final int maxDepth;
    private final int minSegmentRows;
    private final DiagnosticsCalculator diagnostics = new DiagnosticsCalculator(false, 1);

    public HierarchicalSplitOptimizer(int maxDepth, int minSegmentRows) {
        this.maxDepth = Math.max(0, maxDepth);
        this.minSegmentRows = Math.max(1, minSegmentRows);
    }

    @Override
    public String name() { return "hierarchical"; }

    @Override
    public FilteredImage optimize(RawImage image, CandidateGenerator candidates) {
        if (image.height() == 0) return new FilteredImage(image, List.of());
        List<List<FilteredRow>> perRowCandidates = new ArrayList<>(image.height());
        for (int y = 0; y < image.height(); y++) perRowCandidates.add(candidates.generateCandidates(image, y));

        List<FilteredRow> incumbent = FIXED_FILTERS.stream()
                .map(filter -> buildFixedRows(image, perRowCandidates, filter))
                .min(Comparator.comparingLong(rows -> score(image, rows)))
                .orElseThrow();

        for (int depth = 0; depth <= maxDepth; depth++) {
            List<int[]> segments = segmentsAtDepth(image.height(), depth);
            boolean improvedInPass = false;
            for (int[] segment : segments) {
                int start = segment[0];
                int end = segment[1];
                if (end - start < minSegmentRows) continue;
                long incumbentScore = score(image, incumbent);
                List<FilteredRow> bestRows = incumbent;
                long bestScore = incumbentScore;
                for (PngFilter filter : FIXED_FILTERS) {
                    List<FilteredRow> candidate = replaceSegment(image, perRowCandidates, incumbent, start, end, filter);
                    long score = score(image, candidate);
                    if (score < bestScore) {
                        bestScore = score;
                        bestRows = candidate;
                    }
                }
                if (bestScore < incumbentScore) {
                    incumbent = bestRows;
                    improvedInPass = true;
                }
            }
            if (depth > 0 && !improvedInPass) break;
        }

        return new FilteredImage(image, incumbent);
    }

    private long score(RawImage source, List<FilteredRow> rows) {
        byte[] stream = diagnostics.toDeflateInputStream(new FilteredImage(source, rows));
        long repeated16 = repeatedSubstrings(stream, 16);
        long repeated32 = repeatedSubstrings(stream, 32);
        long repeated64 = repeatedSubstrings(stream, 64);
        return stream.length - (repeated16 + (2 * repeated32) + (4 * repeated64));
    }

    private static long repeatedSubstrings(byte[] stream, int width) {
        if (stream.length < width) return 0;
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        long repeated = 0;
        for (int i = 0; i <= stream.length - width; i++) {
            int h = 1;
            for (int j = 0; j < width; j++) h = (31 * h) + (stream[i + j] & 0xff);
            if (!seen.add(h)) repeated++;
        }
        return repeated;
    }

    private static List<FilteredRow> buildFixedRows(RawImage image, List<List<FilteredRow>> perRowCandidates, PngFilter filter) {
        List<FilteredRow> out = new ArrayList<>(image.height());
        for (int y = 0; y < image.height(); y++) {
            out.add(selectCandidate(image, perRowCandidates, y, filter));
        }
        return out;
    }

    private static List<FilteredRow> replaceSegment(RawImage image, List<List<FilteredRow>> perRowCandidates, List<FilteredRow> incumbent, int start, int end, PngFilter filter) {
        List<FilteredRow> out = new ArrayList<>(incumbent);
        for (int y = start; y < end; y++) out.set(y, selectCandidate(image, perRowCandidates, y, filter));
        return out;
    }

    private static FilteredRow selectCandidate(RawImage image, List<List<FilteredRow>> perRowCandidates, int y, PngFilter filter) {
        return perRowCandidates.get(y).stream().filter(c -> c.filter() == filter).findFirst().orElse(new FilteredRow(y, PngFilter.NONE, image.rows().get(y)));
    }

    private static List<int[]> segmentsAtDepth(int height, int depth) {
        int count = 1 << Math.min(depth, 30);
        List<int[]> segments = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int start = (i * height) / count;
            int end = ((i + 1) * height) / count;
            if (start < end) segments.add(new int[]{start, end});
        }
        return segments;
    }
}
