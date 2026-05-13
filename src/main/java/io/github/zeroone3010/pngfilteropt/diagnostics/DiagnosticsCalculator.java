package io.github.zeroone3010.pngfilteropt.diagnostics;

import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.lz.GreedyLzEstimator;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.FilteredRow;

import java.util.*;

public final class DiagnosticsCalculator {
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
            if (count > 0) {
                distinct++;
                double p = (double) count / Math.max(1, stream.length);
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }

        List<byte[]> rowPayloads = image.rows().stream().map(FilteredRow::filteredBytes).toList();
        int rowsEqualToPrevious = 0;
        for (int i = 1; i < rowPayloads.size(); i++) {
            if (Arrays.equals(rowPayloads.get(i), rowPayloads.get(i - 1))) rowsEqualToPrevious++;
        }
        Map<Integer, Integer> rowHashCounts = new HashMap<>();
        for (byte[] r : rowPayloads) rowHashCounts.merge(Arrays.hashCode(r), 1, Integer::sum);
        int mostCommonRowHash = rowHashCounts.values().stream().max(Integer::compareTo).orElse(0);
        int repeatedFullRowCount = (int) rowHashCounts.values().stream().filter(v -> v > 1).mapToInt(v -> v - 1).sum();

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
                longestMatch(stream, 32768),
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

    private int longestMatch(byte[] data, int window) {
        int best = 0;
        for (int i = 1; i < data.length; i++) {
            int start = Math.max(0, i - window);
            for (int j = start; j < i; j++) {
                int k = 0;
                while (i + k < data.length && data[j + k] == data[i + k] && j + k < i) k++;
                if (k > best) best = k;
            }
        }
        return best;
    }

    private long greedySavings(byte[] stream) {
        GreedyLzEstimator estimator = new GreedyLzEstimator();
        int encoded = estimator.estimateCompressedSize(stream);
        return Math.max(0, stream.length - encoded);
    }
}
