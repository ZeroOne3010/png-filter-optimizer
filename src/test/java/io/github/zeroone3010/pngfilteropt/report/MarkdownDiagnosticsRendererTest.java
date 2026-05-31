package io.github.zeroone3010.pngfilteropt.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkdownDiagnosticsRendererTest {
    @Test
    void formatsDiagnosticCountsWithFigureSpaceThousandsSeparators() {
        assertEquals("1\u2007234\u2007567", MarkdownDiagnosticsRenderer.formatNumber(1_234_567));
        assertEquals("-9\u2007876", MarkdownDiagnosticsRenderer.formatNumber(-9_876));
    }
}
