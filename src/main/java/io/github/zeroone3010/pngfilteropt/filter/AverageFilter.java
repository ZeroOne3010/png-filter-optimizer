package io.github.zeroone3010.pngfilteropt.filter;

public class AverageFilter extends NoneFilter {
    @Override
    public PngFilter type() {
        return PngFilter.AVERAGE;
    }
}
