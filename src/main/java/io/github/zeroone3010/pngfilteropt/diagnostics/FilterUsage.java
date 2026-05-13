package io.github.zeroone3010.pngfilteropt.diagnostics;

import io.github.zeroone3010.pngfilteropt.filter.PngFilter;

import java.util.EnumMap;
import java.util.Map;

public record FilterUsage(Map<PngFilter, Integer> counts, int totalRows) {
    public static FilterUsage fromRows(Iterable<PngFilter> filters) {
        EnumMap<PngFilter, Integer> counts = new EnumMap<>(PngFilter.class);
        for (PngFilter filter : PngFilter.values()) counts.put(filter, 0);
        int total = 0;
        for (PngFilter filter : filters) {
            counts.put(filter, counts.get(filter) + 1);
            total++;
        }
        return new FilterUsage(counts, total);
    }

    public double percentage(PngFilter filter) {
        if (totalRows == 0) return 0d;
        return (100.0 * counts.getOrDefault(filter, 0)) / totalRows;
    }
}
