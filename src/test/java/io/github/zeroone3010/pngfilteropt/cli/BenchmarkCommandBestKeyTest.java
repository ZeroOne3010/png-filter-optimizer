package io.github.zeroone3010.pngfilteropt.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkCommandBestKeyTest {
    @Test
    void positiveVisualizationFlagKeepsDefaultEnabledAndNegativeFlagDisablesIt() throws Exception {
        assertTrue(parseFilterVisualizations("--filter-visualizations"));
        assertFalse(parseFilterVisualizations("--no-filter-visualizations"));
    }

    @Test
    void demotesGeneticWhenItMatchesFixedFilterSize() throws Exception {
        Map<String, Long> sizes = new LinkedHashMap<>();
        sizes.put("genetic", 100L);
        sizes.put("fixed-paeth", 100L);
        sizes.put("adaptive", 101L);

        Map<String, Long> timings = new LinkedHashMap<>();
        timings.put("genetic", 1200L);
        timings.put("fixed-paeth", 5L);
        timings.put("adaptive", 3L);

        assertEquals("fixed-paeth", invokeBestKey(sizes, timings));
    }

    @Test
    void usesTimingToBreakNonGeneticTies() throws Exception {
        Map<String, Long> sizes = new LinkedHashMap<>();
        sizes.put("fixed-sub", 100L);
        sizes.put("adaptive", 100L);

        Map<String, Long> timings = new LinkedHashMap<>();
        timings.put("fixed-sub", 11L);
        timings.put("adaptive", 7L);

        assertEquals("adaptive", invokeBestKey(sizes, timings));
    }

    @Test
    void visualizationFilenamesIncludeHashToAvoidSanitizedLabelCollisions() throws Exception {
        String nested = invokeVisualizationFileName("foo/bar.png", "adaptive");
        String flattened = invokeVisualizationFileName("foo-bar.png", "adaptive");

        assertEquals("foo-bar.png--7c48339d--adaptive.filters.png", nested);
        assertEquals("foo-bar.png--c5c812e6--adaptive.filters.png", flattened);
    }

    private static boolean parseFilterVisualizations(String flag) throws Exception {
        BenchmarkCommand command = new BenchmarkCommand();
        new CommandLine(command).parseArgs(flag);
        Field field = BenchmarkCommand.class.getDeclaredField("filterVisualizations");
        field.setAccessible(true);
        return field.getBoolean(command);
    }

    private static String invokeBestKey(Map<String, Long> sizes, Map<String, Long> timings) throws Exception {
        Method method = BenchmarkCommand.class.getDeclaredMethod("bestKey", Map.class, Map.class);
        method.setAccessible(true);
        return (String) method.invoke(null, sizes, timings);
    }

    private static String invokeVisualizationFileName(String imageLabel, String strategy) throws Exception {
        Method method = BenchmarkCommand.class.getDeclaredMethod("visualizationFileName", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, imageLabel, strategy);
    }
}
