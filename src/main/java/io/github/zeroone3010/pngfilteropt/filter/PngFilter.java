package io.github.zeroone3010.pngfilteropt.filter;

public enum PngFilter {
    NONE(0), SUB(1), UP(2), AVERAGE(3), PAETH(4);

    private final int typeByte;

    PngFilter(int typeByte) {
        this.typeByte = typeByte;
    }

    public int typeByte() {
        return typeByte;
    }
}
