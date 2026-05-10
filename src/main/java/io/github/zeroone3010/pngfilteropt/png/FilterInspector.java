package io.github.zeroone3010.pngfilteropt.png;

import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import java.util.EnumMap;
import java.util.Map;

public class FilterInspector {
    public Map<PngFilter, Integer> countFilters(FilteredImage image) {
        Map<PngFilter, Integer> counts = new EnumMap<>(PngFilter.class);
        for (FilteredRow row : image.rows()) {
            counts.merge(row.filter(), 1, Integer::sum);
        }
        return counts;
    }
}
