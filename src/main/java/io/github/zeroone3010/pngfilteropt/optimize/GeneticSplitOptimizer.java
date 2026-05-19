package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.FilteredRow;
import io.github.zeroone3010.pngfilteropt.png.RawImage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.zip.Deflater;

public final class GeneticSplitOptimizer implements FilterOptimizer {
    private static final List<PngFilter> FILTERS = List.of(PngFilter.NONE, PngFilter.SUB, PngFilter.UP, PngFilter.AVERAGE, PngFilter.PAETH);
    private final int blocks;
    private final int population;
    private final int survivors;
    private final double mutationRate;
    private final int runs;
    private final long seed;
    private final Duration timeLimit;
    private String lastRun;

    public GeneticSplitOptimizer(int blocks, int population, int survivors, double mutationRate, int runs, long seed, Duration timeLimit) {
        this.blocks = Math.max(1, blocks);
        this.population = Math.max(2, population);
        this.survivors = Math.max(2, Math.min(survivors, population));
        this.mutationRate = Math.max(0.0, Math.min(1.0, mutationRate));
        this.runs = Math.max(1, runs);
        this.seed = seed;
        this.timeLimit = timeLimit.isNegative() ? Duration.ZERO : timeLimit;
    }

    @Override public String name() { return "genetic"; }
    @Override public Optional<String> explainLastRun() { return Optional.ofNullable(lastRun); }

    @Override
    public FilteredImage optimize(RawImage image, CandidateGenerator candidates) {
        long deadline = System.nanoTime() + timeLimit.toNanos();
        Random random = new Random(seed);
        var scorer = FastDeflateScorer.autoDetect();
        List<List<FilteredRow>> rowCandidates = new ArrayList<>(image.height());
        for (int y = 0; y < image.height(); y++) rowCandidates.add(candidates.generateCandidates(image, y));

        Genome best = null;
        long bestScore = Long.MAX_VALUE;
        StringBuilder log = new StringBuilder("optimizer=genetic blocks=" + blocks + " population=" + population + " runs=" + runs + " scorer=" + scorer.name + "\n");

        int iterations = 0;
        for (int run = 0; run < runs && System.nanoTime() < deadline; run++) {
            List<Genome> pool = randomPopulation(random);
            while (pool.size() > 1 && System.nanoTime() < deadline) {
                List<ScoredGenome> scored = new ArrayList<>(pool.size());
                for (Genome g : pool) {
                    long score = scorer.score(toDeflateStream(image, rowCandidates, g));
                    scored.add(new ScoredGenome(g, score));
                    if (score < bestScore) {
                        bestScore = score;
                        best = g;
                        log.append("best@iter=").append(iterations).append(" score=").append(score).append(" config=").append(g.code()).append("\n");
                    }
                }
                scored.sort(Comparator.comparingLong(ScoredGenome::score));
                List<Genome> parents = scored.stream().limit(survivors).map(ScoredGenome::genome).toList();
                List<Genome> next = new ArrayList<>();
                next.add(parents.get(0));
                while (next.size() < Math.max(1, pool.size() / 2)) {
                    Genome a = parents.get(random.nextInt(parents.size()));
                    Genome b = parents.get(random.nextInt(parents.size()));
                    next.add(a.breedWith(b, random, mutationRate));
                }
                pool = next;
                iterations++;
            }
        }

        if (best == null) best = new Genome(new PngFilter[blocks]);
        lastRun = log.append("final_best=").append(best.code()).append(" score=").append(bestScore).toString();
        return new FilteredImage(image, materializeRows(image, rowCandidates, best));
    }

    private List<Genome> randomPopulation(Random random) {
        List<Genome> genomes = new ArrayList<>(population);
        for (int i = 0; i < population; i++) {
            PngFilter[] blockFilters = new PngFilter[blocks];
            for (int b = 0; b < blocks; b++) blockFilters[b] = FILTERS.get(random.nextInt(FILTERS.size()));
            genomes.add(new Genome(blockFilters));
        }
        return genomes;
    }

    private List<FilteredRow> materializeRows(RawImage image, List<List<FilteredRow>> perRow, Genome genome) {
        List<FilteredRow> out = new ArrayList<>(image.height());
        for (int y = 0; y < image.height(); y++) {
            int block = Math.min(blocks - 1, (y * blocks) / Math.max(1, image.height()));
            PngFilter f = genome.blockFilters[block] == null ? PngFilter.NONE : genome.blockFilters[block];
            FilteredRow selected = perRow.get(y).stream().filter(c -> c.filter() == f).findFirst().orElse(perRow.get(y).get(0));
            out.add(selected);
        }
        return out;
    }

    private byte[] toDeflateStream(RawImage image, List<List<FilteredRow>> perRow, Genome genome) {
        List<FilteredRow> rows = materializeRows(image, perRow, genome);
        int rowLen = image.width() * image.bytesPerPixel() + 1;
        byte[] out = new byte[rowLen * image.height()];
        int p = 0;
        for (FilteredRow row : rows) {
            out[p++] = (byte) row.filter().pngValue();
            System.arraycopy(row.bytes(), 0, out, p, row.bytes().length);
            p += row.bytes().length;
        }
        return out;
    }

    private record Genome(PngFilter[] blockFilters) {
        Genome breedWith(Genome other, Random random, double mutationRate) {
            PngFilter[] child = new PngFilter[blockFilters.length];
            int split = blockFilters.length / 2;
            for (int i = 0; i < blockFilters.length; i++) child[i] = i < split ? blockFilters[i] : other.blockFilters[i];
            if (random.nextDouble() < mutationRate) child[random.nextInt(child.length)] = FILTERS.get(random.nextInt(FILTERS.size()));
            return new Genome(child);
        }
        String code() {
            StringBuilder b = new StringBuilder(blockFilters.length);
            for (PngFilter f : blockFilters) b.append(switch (f == null ? PngFilter.NONE : f) { case NONE -> 'N'; case SUB -> 'S'; case UP -> 'U'; case AVERAGE -> 'A'; case PAETH -> 'P';});
            return b.toString();
        }
    }
    private record ScoredGenome(Genome genome, long score) {}

    private static final class FastDeflateScorer {
        private final String name; private final List<String> cmd;
        private FastDeflateScorer(String name, List<String> cmd) { this.name = name; this.cmd = cmd; }
        static FastDeflateScorer autoDetect() {
            List<FastDeflateScorer> options = List.of(
                    new FastDeflateScorer("pigz-1", List.of("pigz", "-1", "-c")),
                    new FastDeflateScorer("gzip-1", List.of("gzip", "-1", "-c")),
                    new FastDeflateScorer("zlib-java-1", List.of())
            );
            for (FastDeflateScorer s : options) {
                if (s.cmd.isEmpty()) return s;
                try {
                    Process p = new ProcessBuilder(s.cmd).start();
                    p.destroyForcibly();
                    return s;
                } catch (IOException ignored) {}
            }
            return options.get(options.size() - 1);
        }
        long score(byte[] input) {
            if (cmd.isEmpty()) return zlibLen(input);
            try {
                Path in = Files.createTempFile("png-ga", ".bin");
                Files.write(in, input);
                Process p = new ProcessBuilder(cmd).redirectInput(in.toFile()).start();
                byte[] out = p.getInputStream().readAllBytes();
                int exit = p.waitFor();
                Files.deleteIfExists(in);
                if (exit == 0) return out.length;
            } catch (Exception ignored) {}
            return zlibLen(input);
        }
        private static int zlibLen(byte[] input) {
            Deflater d = new Deflater(1, true);
            d.setInput(input); d.finish();
            byte[] buf = new byte[input.length + 256];
            int n = d.deflate(buf); d.end();
            return n;
        }
    }
}
