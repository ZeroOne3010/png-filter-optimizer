package io.github.zeroone3010.pngfilteropt.png;

import ar.com.hjg.pngj.PngReaderByte;
import ar.com.hjg.pngj.chunks.PngChunkPLTE;
import ar.com.hjg.pngj.chunks.PngChunkTRNS;

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
            byte[] palette = null;
            byte[] trns = null;
            if (info.indexed) {
                PngChunkPLTE plte = reader.getMetadata().getPLTE();
                if (plte != null) {
                    int n = plte.getNentries();
                    palette = new byte[n * 3];
                    for (int i = 0; i < n; i++) {
                        int rgb = plte.getEntry(i);
                        palette[i * 3] = (byte) ((rgb >> 16) & 0xFF);
                        palette[i * 3 + 1] = (byte) ((rgb >> 8) & 0xFF);
                        palette[i * 3 + 2] = (byte) (rgb & 0xFF);
                    }
                }
                PngChunkTRNS trnsChunk = reader.getMetadata().getTRNS();
                if (trnsChunk != null && trnsChunk.getPalletteAlpha() != null) {
                    int[] alpha = trnsChunk.getPalletteAlpha();
                    trns = new byte[alpha.length];
                    for (int i = 0; i < alpha.length; i++) {
                        trns[i] = (byte) (alpha[i] & 0xFF);
                    }
                }
            }

            return new RawImage(
                    info.cols,
                    info.rows,
                    info.bitDepth,
                    toPngColorType(info.indexed, info.greyscale, info.alpha),
                    info.bytesPixel,
                    info.bytesPerRow,
                    rows,
                    palette,
                    trns,
                    info.interlaced ? 1 : 0
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
