package io.github.zeroone3010.pngfilteropt.png;

import io.github.zeroone3010.pngfilteropt.filter.PngFilter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.zip.InflaterInputStream;

public final class FilterInspector {
    public Map<PngFilter, Integer> countFilters(Path pngPath, RawImage image) {
        Map<PngFilter, Integer> counts = zeroCounts();
        for (byte filterByte : readScanlineFilterBytes(pngPath, image.height(), image.bytesPerRow())) {
            PngFilter filter = fromPngFilterByte(filterByte);
            counts.merge(filter, 1, Integer::sum);
        }
        return counts;
    }

    public Map<PngFilter, Integer> countFilters(FilteredImage image) {
        Map<PngFilter, Integer> counts = zeroCounts();
        for (FilteredRow row : image.rows()) {
            counts.merge(row.filter(), 1, Integer::sum);
        }
        return counts;
    }

    private static Map<PngFilter, Integer> zeroCounts() {
        Map<PngFilter, Integer> counts = new EnumMap<>(PngFilter.class);
        for (PngFilter filter : PngFilter.values()) {
            counts.put(filter, 0);
        }
        return counts;
    }

    private static PngFilter fromPngFilterByte(byte filterByte) {
        int value = Byte.toUnsignedInt(filterByte);
        return Arrays.stream(PngFilter.values())
                .filter(f -> f.pngValue() == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported PNG filter byte: " + value));
    }

    private static byte[] readScanlineFilterBytes(Path pngPath, int rows, int bytesPerRow) {
        try {
            byte[] png = Files.readAllBytes(pngPath);
            byte[] idat = extractIdatPayload(png);
            byte[] decompressed = inflate(idat);
            int stride = 1 + bytesPerRow;
            if (decompressed.length < rows * stride) {
                throw new IllegalArgumentException("Inflated IDAT data shorter than expected for non-interlaced image");
            }
            byte[] filters = new byte[rows];
            for (int y = 0; y < rows; y++) {
                filters[y] = decompressed[y * stride];
            }
            return filters;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to inspect PNG filters from: " + pngPath, e);
        }
    }

    private static byte[] extractIdatPayload(byte[] pngBytes) throws IOException {
        final byte[] signature = new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        if (pngBytes.length < signature.length || !Arrays.equals(signature, Arrays.copyOf(pngBytes, signature.length))) {
            throw new IllegalArgumentException("Not a PNG file");
        }

        ByteArrayOutputStream idat = new ByteArrayOutputStream();
        int pos = signature.length;
        while (pos + 8 <= pngBytes.length) {
            int length = readInt32(pngBytes, pos);
            pos += 4;
            String type = new String(pngBytes, pos, 4);
            pos += 4;
            if (pos + length + 4 > pngBytes.length) {
                throw new IllegalArgumentException("Malformed PNG chunk stream");
            }
            if ("IDAT".equals(type)) {
                idat.write(pngBytes, pos, length);
            }
            pos += length + 4;
            if ("IEND".equals(type)) {
                break;
            }
        }
        return idat.toByteArray();
    }

    private static int readInt32(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    private static byte[] inflate(byte[] compressed) throws IOException {
        try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }
}
