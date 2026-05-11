package io.github.zeroone3010.pngfilteropt.png;

import ar.com.hjg.pngj.PngReaderByte;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PngDecoder {
    public RawImage decode(Path path) {
        PngReaderByte reader = new PngReaderByte(path.toFile());
        try {
            var info = reader.imgInfo;
            List<byte[]> rows = new ArrayList<>(info.rows);
            for (int y = 0; y < info.rows; y++) {
                rows.add(reader.readRowByte().getScanline().clone());
            }
            return new RawImage(
                    info.cols,
                    info.rows,
                    info.bitDepth,
                    toPngColorType(info.indexed, info.greyscale, info.alpha),
                    info.bytesPixel,
                    info.bytesPerRow,
                    rows
            );
        } finally {
            reader.end();
        }
    }

    private static int toPngColorType(boolean indexed, boolean greyscale, boolean alpha) {
        if (indexed) {
            return 3;
        }
        if (greyscale) {
            return alpha ? 4 : 0;
        }
        return alpha ? 6 : 2;
    }
}
