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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class DiagnosticsCalculator {
    private final DirectionalityAnalyzer directionalityAnalyzer = new DirectionalityAnalyzer();
    private final int lzMaxCandidates;
    private final boolean includeLzDiagnostics;

    public DiagnosticsCalculator() { this(true, 16); }

    public DiagnosticsCalculator(int lzMaxCandidates) { this(true, lzMaxCandidates); }

    public DiagnosticsCalculator(boolean includeLzDiagnostics, int lzMaxCandidates) {
        this.includeLzDiagnostics = includeLzDiagnostics;
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
        var lz = includeLzDiagnostics
                ? new LzGreedyDiagnostics(lzMaxCandidates).analyze(stream)
                : new LzParseDiagnostics(0, 0, 0, stream.length, 0, 0, 0, new long[6], new long[5], 0, 0, 0, 0);
        RepetitionMetrics rep = repetitionMetrics(stream, lz.maxMatchLength());
        var src = image.source();
        var directional = directionalityAnalyzer.directionalSmoothness(src);
        var residual = directionalityAnalyzer.residualDiagnostics(src);
        return new FilteredStreamDiagnostics(
                src.width(), src.height(), src.colorType(), src.bitDepth(), src.bytesPerPixel(), src.bytesPerRow(),
                stream.length, entropy, zeros, stream.length == 0 ? 0d : (100.0 * zeros / stream.length),
                distinct, longestRun, repeatedFullRowCount, rowsEqualToPrevious, mostCommonRowHash,
                filterUsage, rep, lz, directional, residual, sha256(stream)
        );
    }

    private String sha256(byte[] stream) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
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

    private RepetitionMetrics repetitionMetrics(byte[] stream, int longestMatch) {
        return new RepetitionMetrics(
                repeatedSubstrings(stream, 16),
                repeatedSubstrings(stream, 32),
                repeatedSubstrings(stream, 64),
                longestMatch,
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

    private long greedySavings(byte[] stream) {
        GreedyLzEstimator estimator = new GreedyLzEstimator(new HashChainLzHistory());
        int encoded = estimator.estimateCompressedCost(stream);
        return Math.max(0, stream.length - encoded);
    }
}
