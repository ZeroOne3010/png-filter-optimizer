package io.github.zeroone3010.pngfilteropt.filter;

public class PaethFilter extends NoneFilter {
    @Override
    public PngFilter type() {
        return PngFilter.PAETH;
    }
}
