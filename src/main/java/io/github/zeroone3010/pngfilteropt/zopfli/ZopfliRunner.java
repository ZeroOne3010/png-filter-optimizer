package io.github.zeroone3010.pngfilteropt.zopfli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ZopfliRunner {
    public long recompress(Path input, Path output, Path executable, boolean preserveFilters) {
        ProcessBuilder pb = preserveFilters
                ? new ProcessBuilder(executable.toString(), "-y", "--filters=p", input.toString(), output.toString())
                : new ProcessBuilder(executable.toString(), "-y", input.toString(), output.toString());
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        try {
            int exit = pb.start().waitFor();
            if (exit != 0) {
                throw new IllegalStateException("zopflipng failed with exit code " + exit);
            }
            return Files.size(output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to execute zopflipng", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to execute zopflipng", e);
        }
    }
}
