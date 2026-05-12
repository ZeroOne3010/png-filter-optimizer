package io.github.zeroone3010.pngfilteropt.png;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

public final class PngEncoder {
    public void encode(FilteredImage filteredImage, Path output) {
        try {
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            Files.write(output, toPngBytes(filteredImage));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode PNG: " + output, e);
        }
    }

    private byte[] toPngBytes(FilteredImage image) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.write(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});

        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        writeInt(ihdr, image.source().width());
        writeInt(ihdr, image.source().height());
        ihdr.write(image.source().bitDepth());
        ihdr.write(image.source().colorType());
        ihdr.write(0);
        ihdr.write(0);
        ihdr.write(0);
        writeChunk(png, "IHDR", ihdr.toByteArray());
        if (image.source().colorType() == 3) {
            if (image.source().paletteRgb() == null || image.source().paletteRgb().length == 0) {
                throw new IllegalStateException("Indexed-color PNG requires PLTE palette data");
            }
            writeChunk(png, "PLTE", image.source().paletteRgb());
            if (image.source().transparencyAlpha() != null && image.source().transparencyAlpha().length > 0) {
                writeChunk(png, "tRNS", image.source().transparencyAlpha());
            }
        }

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        for (FilteredRow row : image.rows()) {
            raw.write(row.filter().pngValue());
            raw.write(row.filteredBytes());
        }
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            deflater.write(raw.toByteArray());
        }
        writeChunk(png, "IDAT", compressed.toByteArray());
        writeChunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        writeInt(out, data.length);
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc32 = new CRC32();
        crc32.update(typeBytes);
        crc32.update(data);
        writeInt(out, (int) crc32.getValue());
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
