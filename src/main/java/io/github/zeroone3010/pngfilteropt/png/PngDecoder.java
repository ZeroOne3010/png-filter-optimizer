package io.github.zeroone3010.pngfilteropt.png;

import ar.com.hjg.pngj.PngReaderByte;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PngDecoder {
    public RawImage decode(Path path) {
        try (PngReaderByte reader = new PngReaderByte(path.toFile())) {
            var info = reader.imgInfo;
            List<byte[]> rows = new ArrayList<>(info.rows);
            for (int y = 0; y < info.rows; y++) {
                rows.add(reader.readRowByte(y).getScanline().clone());
            }
            return new RawImage(info.cols, info.rows, info.bitDepth, info.channels, info.channels, info.bytesPerRow, rows);
        }
    }
}
