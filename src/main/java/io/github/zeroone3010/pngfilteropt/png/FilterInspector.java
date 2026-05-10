package io.github.zeroone3010.pngfilteropt.png;

import io.github.zeroone3010.pngfilteropt.filter.PngFilter;

import java.util.EnumMap;
import java.util.Map;

public final class FilterInspector {
    public Map<PngFilter, Integer> countFilters(FilteredImage image) {
        Map<PngFilter, Integer> counts = new EnumMap<>(PngFilter.class);
        for (PngFilter filter : PngFilter.values()) {
            counts.put(filter, 0);
        }
        for (FilteredRow row : image.rows()) {
            counts.merge(row.filter(), 1, Integer::sum);
        }
        return counts;
    }
}
