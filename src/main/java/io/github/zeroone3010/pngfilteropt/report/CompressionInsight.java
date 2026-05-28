package io.github.zeroone3010.pngfilteropt.report;

import java.util.List;

public record CompressionInsight(List<CompressionPattern> patterns, String summary) {
}
