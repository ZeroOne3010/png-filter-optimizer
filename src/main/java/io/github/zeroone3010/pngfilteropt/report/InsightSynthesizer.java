package io.github.zeroone3010.pngfilteropt.report;

import io.github.zeroone3010.pngfilteropt.diagnostics.FilteredStreamDiagnostics;
import io.github.zeroone3010.pngfilteropt.diagnostics.LzParseDiagnostics;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class InsightSynthesizer {
    private static final double SMALL_DELTA = 0.05;
    private static final double STRONG_DELTA = 0.20;

    public CompressionInsight synthesize(ExplanationContext context) {
        FilteredStreamDiagnostics winner = context.winner();
        if (winner == null) {
            return new CompressionInsight(List.of(), "Compression behavior is inconclusive because diagnostics are unavailable for the winning strategy.");
        }
        List<CompressionPattern> patterns = detectPatterns(context);
        return new CompressionInsight(patterns, renderSummary(context, patterns));
    }

    public List<CompressionPattern> detectPatterns(ExplanationContext context) {
        List<CompressionPattern> patterns = new ArrayList<>();
        FilteredStreamDiagnostics winner = context.winner();
        if (winner == null) return patterns;

        String localBest = context.localBestFilter();
        double dominantShare = context.dominantWinnerShare();
        PngFilter dominant = context.dominantWinnerFilter();
        boolean fixed = context.fixedStrategyFilter().isPresent();
        double direction = winner.directionalSmoothness().verticalHorizontalRatio();

        if ("fixed-none".equals(context.best()) && (winner.rowsEqualToPrevious() > 0 || winner.lzParseDiagnostics().averageMatchLength() >= 20.0)) {
            patterns.add(pattern(CompressionPattern.Type.LITERAL_STRUCTURE_PRESERVATION,
                    "Preserving exact row bytes creates reusable DEFLATE phrases.", 0.95));
        }

        if ("PAETH".equals(localBest) && !context.bestUsesFilter("PAETH")) {
            patterns.add(pattern(CompressionPattern.Type.SIMPLER_PREDICTORS_BEAT_PAETH,
                    "PAETH is locally accurate, but a simpler residual language wins globally.", 0.85));
        }

        if (fixed || dominantShare >= 85.0) {
            patterns.add(pattern(CompressionPattern.Type.CONSISTENCY_DOMINATES,
                    "A globally consistent predictor is more valuable than frequent local switching.", fixed ? 0.90 : dominantShare / 100.0));
        }

        if (!fixed && dominantShare >= 70.0 && context.distinctWinnerFilters() >= 2 && hasSimilarFixedAlternative(context, dominant)) {
            patterns.add(pattern(CompressionPattern.Type.USEFUL_LOCAL_EXCEPTIONS,
                    "A dominant global predictor benefits from small localized exceptions.", dominantShare / 100.0));
        }

        context.runnerUpByLzCost().ifPresent(runnerEntry -> {
            FilteredStreamDiagnostics runner = runnerEntry.getValue();
            LzParseDiagnostics winnerLz = winner.lzParseDiagnostics();
            LzParseDiagnostics runnerLz = runner.lzParseDiagnostics();
            boolean similarRepetition = Math.abs(context.repeated32DeltaVsRunner()) < SMALL_DELTA;
            boolean betterCost = winnerLz.approximateLzCostBits() < runnerLz.approximateLzCostBits();
            boolean betterLength = winnerLz.averageMatchLength() > runnerLz.averageMatchLength() * 1.10;
            boolean betterCoverage = winnerLz.matchCoveragePercent() > runnerLz.matchCoveragePercent() + 3.0;
            if (similarRepetition && betterCost && (betterLength || betterCoverage)) {
                patterns.add(pattern(CompressionPattern.Type.MATCH_QUALITY_DOMINATES,
                        "Match usefulness matters more than raw repeated-substring counts.", Math.min(1.0, 0.55 + context.winnerVsRunnerCostImprovement())));
            }
        });

        if ((direction > 1.20 && (context.bestUsesFilter("SUB") || dominant == PngFilter.SUB))
                || (direction < 0.85 && (context.bestUsesFilter("UP") || dominant == PngFilter.UP))) {
            patterns.add(pattern(CompressionPattern.Type.DIRECTIONAL_COHERENCE,
                    "The image has a strong directional predictor family.", Math.abs(Math.log(direction))));
        }

        if (context.distinctWinnerFilters() >= 3 && dominantShare > 0.0 && dominantShare < 65.0) {
            patterns.add(pattern(CompressionPattern.Type.MIXED_CONTENT,
                    "Competing local structures prevent one predictor from dominating the whole image.", 0.65));
        }

        patterns.add(pattern(CompressionPattern.Type.IMAGE_BEHAVIOR, classifyImageBehavior(context), 0.50));
        return patterns;
    }

    private CompressionPattern pattern(CompressionPattern.Type type, String description, double strength) {
        return new CompressionPattern(type, description, Math.max(0.0, Math.min(1.0, strength)));
    }

    private boolean hasSimilarFixedAlternative(ExplanationContext context, PngFilter dominant) {
        String fixedKey = "fixed-" + dominant.name().toLowerCase(Locale.ROOT).replace('_', '-');
        var fixed = context.diagnostics().get(fixedKey);
        if (fixed == null || context.winner() == null) return false;
        long fixedCost = fixed.lzParseDiagnostics().approximateLzCostBits();
        long winnerCost = context.winner().lzParseDiagnostics().approximateLzCostBits();
        if (fixedCost <= 0) return false;
        return Math.abs(fixedCost - winnerCost) / (double) fixedCost < 0.12;
    }

    private String classifyImageBehavior(ExplanationContext context) {
        FilteredStreamDiagnostics winner = context.winner();
        double direction = winner.directionalSmoothness().verticalHorizontalRatio();
        double dominantShare = context.dominantWinnerShare();
        int distinct = context.distinctWinnerFilters();
        if ("fixed-none".equals(context.best()) && winner.rowsEqualToPrevious() > 0) {
            return "The image resembles symbolic raster graphics, a UI asset, or repeated macrostructure where exact rows matter.";
        }
        if (winner.colorType() == 4 || winner.colorType() == 6 && context.fixedStrategyFilter().orElse(context.dominantWinnerFilter()) == PngFilter.NONE) {
            return "The image may behave like a logo/icon or alpha-heavy graphic with literal transparent or flat regions.";
        }
        if (direction > 1.35 || direction < 0.75) {
            return "The image behaves like a directional gradient or scanline-coherent graphic.";
        }
        if (distinct >= 3 && dominantShare < 65.0) {
            return "The image behaves like mixed content or a painterly/texture-heavy source with several competing structures.";
        }
        if (dominantShare >= 90.0) {
            return "The image behaves like a regular screenshot, gradient, or graphic dominated by one predictor direction.";
        }
        return "The image has moderate structure: enough repetition for DEFLATE, but not a single obvious visual class.";
    }

    private String renderSummary(ExplanationContext context, List<CompressionPattern> patterns) {
        if (has(patterns, CompressionPattern.Type.LITERAL_STRUCTURE_PRESERVATION)) {
            return "Preserving exact repeated row structure outweighs residual minimization here. DEFLATE likely benefits from seeing the same byte phrases recur rather than from locally smaller but less reusable residuals.";
        }
        if (has(patterns, CompressionPattern.Type.SIMPLER_PREDICTORS_BEAT_PAETH) && has(patterns, CompressionPattern.Type.USEFUL_LOCAL_EXCEPTIONS)) {
            return "Simpler predictors appear to form a more regular DEFLATE-friendly residual language than PAETH's locally adaptive predictor. The image behaves mostly like a fixed-" + context.dominantWinnerFilter().name() + " image with a few beneficial local exceptions.";
        }
        if (has(patterns, CompressionPattern.Type.SIMPLER_PREDICTORS_BEAT_PAETH)) {
            return "PAETH minimizes local residuals, but simpler predictors create a more regular DEFLATE-friendly residual language. This suggests global phrase reuse matters more than per-pixel predictor cleverness for this image.";
        }
        if (has(patterns, CompressionPattern.Type.USEFUL_LOCAL_EXCEPTIONS)) {
            return "A dominant global predictor explains most of the image, while localized exceptions improve the residual stream where the main direction breaks down. DEFLATE likely prefers this stable language with occasional corrections over constant adaptation.";
        }
        if (has(patterns, CompressionPattern.Type.MATCH_QUALITY_DOMINATES)) {
            return "The decisive behavior is match quality rather than repetition volume: longer, better-aligned phrases are worth more than simply producing many repeated substrings. DEFLATE likely prefers the strategy that turns repetitions into cheaper tokens.";
        }
        if (has(patterns, CompressionPattern.Type.DIRECTIONAL_COHERENCE)) {
            return "The image's directional structure strongly favors one predictor family. Compression improves when the filter choice preserves that coherent scanline or column language for DEFLATE.";
        }
        if (has(patterns, CompressionPattern.Type.MIXED_CONTENT)) {
            return "The image contains competing local structures, so no single predictor dominates globally. The best result comes from balancing several residual languages rather than forcing one visual assumption everywhere.";
        }
        if (has(patterns, CompressionPattern.Type.CONSISTENCY_DOMINATES)) {
            return "Globally consistent predictors appear more valuable than locally optimal prediction. DEFLATE benefits when the residual stream uses a small, repeatable vocabulary.";
        }
        return patterns.stream()
                .filter(p -> p.type() == CompressionPattern.Type.IMAGE_BEHAVIOR)
                .map(CompressionPattern::description)
                .findFirst()
                .orElse("Compression behavior reflects a close tradeoff between predictor accuracy and reusable DEFLATE phrase structure.");
    }

    private boolean has(List<CompressionPattern> patterns, CompressionPattern.Type type) {
        return patterns.stream().anyMatch(p -> p.type() == type);
    }
}
