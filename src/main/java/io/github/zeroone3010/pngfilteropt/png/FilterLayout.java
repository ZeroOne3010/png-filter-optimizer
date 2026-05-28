package io.github.zeroone3010.pngfilteropt.png;

import io.github.zeroone3010.pngfilteropt.filter.PngFilter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record FilterLayout(int rowCount, List<PngFilter> rowFilters, List<Run> runs) {
    public record Run(int startRow, int endRowInclusive, PngFilter filter) {
        public int rowCount() {
            return endRowInclusive - startRow + 1;
        }
    }

    public static FilterLayout fromRows(List<PngFilter> rowFilters) {
        List<PngFilter> copy = List.copyOf(rowFilters);
        List<Run> runs = new ArrayList<>();
        if (!copy.isEmpty()) {
            int start = 0;
            PngFilter current = copy.getFirst();
            for (int y = 1; y < copy.size(); y++) {
                PngFilter filter = copy.get(y);
                if (filter != current) {
                    runs.add(new Run(start, y - 1, current));
                    start = y;
                    current = filter;
                }
            }
            runs.add(new Run(start, copy.size() - 1, current));
        }
        return new FilterLayout(copy.size(), copy, List.copyOf(runs));
    }

    public Map<PngFilter, Integer> counts() {
        Map<PngFilter, Integer> counts = new EnumMap<>(PngFilter.class);
        for (PngFilter filter : PngFilter.values()) {
            counts.put(filter, 0);
        }
        for (PngFilter filter : rowFilters) {
            counts.merge(filter, 1, Integer::sum);
        }
        return counts;
    }

    public boolean isTrivial() {
        return runs.size() <= 1;
    }
}
