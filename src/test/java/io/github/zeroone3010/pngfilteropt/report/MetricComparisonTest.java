package io.github.zeroone3010.pngfilteropt.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricComparisonTest {
    @Test
    void usesSymmetricRelativeThresholdsAndHandlesEquality() {
        assertEquals("equal", MetricComparison.describe(100, 100));
        assertEquals("broadly similar", MetricComparison.describe(101, 100));
        assertEquals("broadly similar", MetricComparison.describe(104, 100));
        assertEquals("substantially more", MetricComparison.describe(120, 100));
        assertEquals("dramatically more", MetricComparison.describe(160, 100));
    }
}
