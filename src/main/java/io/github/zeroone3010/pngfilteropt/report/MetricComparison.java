package io.github.zeroone3010.pngfilteropt.report;

/** Centralized, symmetric thresholds for honest metric prose. */
public final class MetricComparison {
    private MetricComparison() { }

    public static String describe(long value, long reference) {
        if (value == reference) return "equal";
        double relative = reference == 0 ? Double.POSITIVE_INFINITY
                : Math.abs(value - reference) / (double) Math.abs(reference);
        if (relative < 0.01) return "essentially the same";
        if (relative < 0.05) return "broadly similar";
        String direction = value > reference ? "more" : "fewer";
        if (relative < 0.20) return "moderately " + direction;
        if (relative <= 0.50) return "substantially " + direction;
        return "dramatically " + direction;
    }
}
