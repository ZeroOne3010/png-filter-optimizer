package io.github.zeroone3010.pngfilteropt.diagnostics;

import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.lz.GreedyLzEstimator;
import io.github.zeroone3010.pngfilteropt.lz.HashChainLzHistory;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.FilteredRow;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DiagnosticsCalculator {
    private static final int LOOKBACK_WINDOW = 32 * 1024;
    private static final int MAX_DEFLATE_MATCH = 258;
    private static final int HASH_BYTES = 4;

    private final int lzSampleStep;
    private final int lzMaxCandidates;

    public DiagnosticsCalculator() {
        this(4, 16);
    }

    public DiagnosticsCalculator(int lzSampleStep, int lzMaxCandidates) {
        this.lzSampleStep = Math.max(1, lzSampleStep);
        this.lzMaxCandidates = Math.max(1, lzMaxCandidates);
    }

    public FilteredStreamDiagnostics calculate(FilteredImage image) {
        byte[] stream = toDeflateInputStream(image);
        int[] histogram = new int[256];
        int zeros = 0;
        int longestRun = 0;
        int run = 0;
        int prev = -1;
        for (byte b : stream) {
            int v = b & 0xFF;
            histogram[v]++;
            if (v == 0) zeros++;
            if (v == prev) run++; else run = 1;
            longestRun = Math.max(longestRun, run);
            prev = v;
        }
        int distinct = 0;
        double entropy = 0d;
        for (int count : histogram) {
            if (count <= 0) continue;
            distinct++;
            double p = (double) count / Math.max(1, stream.length);
            entropy -= p * (Math.log(p) / Math.log(2));
        }

        List<byte[]> rowPayloads = image.rows().stream().map(FilteredRow::filteredBytes).toList();
        int rowsEqualToPrevious = 0;
        for (int i = 1; i < rowPayloads.size(); i++) {
            if (Arrays.equals(rowPayloads.get(i), rowPayloads.get(i - 1))) rowsEqualToPrevious++;
        }
        Map<Integer, Integer> rowHashCounts = new HashMap<>();
        for (byte[] r : rowPayloads) rowHashCounts.merge(Arrays.hashCode(r), 1, Integer::sum);
        int mostCommonRowHash = rowHashCounts.values().stream().max(Integer::compareTo).orElse(0);
        int repeatedFullRowCount = rowHashCounts.values().stream().filter(v -> v > 1).mapToInt(v -> v - 1).sum();

        FilterUsage filterUsage = FilterUsage.fromRows(image.rows().stream().map(FilteredRow::filter).toList());
        RepetitionMetrics rep = repetitionMetrics(stream);
        var src = image.source();
        return new FilteredStreamDiagnostics(
                src.width(), src.height(), src.colorType(), src.bitDepth(), src.bytesPerPixel(), src.bytesPerRow(),
                stream.length, entropy, zeros, stream.length == 0 ? 0d : (100.0 * zeros / stream.length),
                distinct, longestRun, repeatedFullRowCount, rowsEqualToPrevious, mostCommonRowHash,
                filterUsage, rep
        );
    }

    public byte[] toDeflateInputStream(FilteredImage image) {
        int len = image.rows().stream().mapToInt(r -> 1 + r.filteredBytes().length).sum();
        byte[] out = new byte[len];
        int pos = 0;
        for (FilteredRow row : image.rows()) {
            out[pos++] = (byte) row.filter().pngValue();
            System.arraycopy(row.filteredBytes(), 0, out, pos, row.filteredBytes().length);
            pos += row.filteredBytes().length;
        }
        return out;
    }

    private RepetitionMetrics repetitionMetrics(byte[] stream) {
        return new RepetitionMetrics(
                repeatedSubstrings(stream, 16),
                repeatedSubstrings(stream, 32),
                repeatedSubstrings(stream, 64),
                longestMatchApprox(stream),
                greedySavings(stream)
        );
    }

    private int repeatedSubstrings(byte[] data, int n) {
        if (data.length < n) return 0;
        Set<Integer> seen = new HashSet<>();
        int repeats = 0;
        for (int i = 0; i <= data.length - n; i++) {
            int h = 1;
            for (int j = 0; j < n; j++) h = 31 * h + (data[i + j] & 0xFF);
            if (!seen.add(h)) repeats++;
        }
        return repeats;
    }

    private int longestMatchApprox(byte[] data) {
        if (data.length < HASH_BYTES) return 0;
        Map<Integer, ArrayDeque<Integer>> positionsByKey = new HashMap<>();
        int best = 0;
        for (int i = 0; i <= data.length - HASH_BYTES; i += lzSampleStep) {
            int key = fourByteKey(data, i);
            ArrayDeque<Integer> positions = positionsByKey.computeIfAbsent(key, ignored -> new ArrayDeque<>());

            int inspected = 0;
            for (var it = positions.descendingIterator(); it.hasNext() && inspected < lzMaxCandidates; inspected++) {
                int prev = it.next();
                if (i - prev > LOOKBACK_WINDOW) continue;
                int match = 0;
                int maxLen = Math.min(MAX_DEFLATE_MATCH, data.length - i);
                while (match < maxLen && data[prev + match] == data[i + match] && prev + match < i) match++;
                best = Math.max(best, match);
            }

            positions.addLast(i);
            while (!positions.isEmpty() && i - positions.peekFirst() > LOOKBACK_WINDOW) positions.removeFirst();
            while (positions.size() > lzMaxCandidates * 4) positions.removeFirst();
        }
        return best;
    }

    private static int fourByteKey(byte[] data, int pos) {
        return ((data[pos] & 0xFF) << 24)
                | ((data[pos + 1] & 0xFF) << 16)
                | ((data[pos + 2] & 0xFF) << 8)
                | (data[pos + 3] & 0xFF);
    }

    private long greedySavings(byte[] stream) {
        GreedyLzEstimator estimator = new GreedyLzEstimator(new HashChainLzHistory());
        int encoded = estimator.estimateCompressedCost(stream);
        return Math.max(0, stream.length - encoded);
    }
}
