package io.github.zeroone3010.pngfilteropt.png;

public interface PngDecoder {
    RawImage decode(byte[] pngData);
}
