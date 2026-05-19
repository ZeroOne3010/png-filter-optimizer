package io.github.zeroone3010.pngfilteropt.optimize;

import io.github.zeroone3010.pngfilteropt.filter.CandidateGenerator;
import io.github.zeroone3010.pngfilteropt.filter.PngFilter;
import io.github.zeroone3010.pngfilteropt.png.FilteredImage;
import io.github.zeroone3010.pngfilteropt.png.FilteredRow;
import io.github.zeroone3010.pngfilteropt.png.RawImage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.Deflater;

public final class GeneticSplitOptimizer implements FilterOptimizer {
    private static final List<PngFilter> FILTERS = List.of(PngFilter.NONE, PngFilter.SUB, PngFilter.UP, PngFilter.AVERAGE, PngFilter.PAETH);
    private final int blocks;
    private final int evaluationsBudget;
    private final int population;
    private final int survivors;
    private final int eliteCount;
    private final int generations;
    private final double mutationRate;
    private final long seed;
    private final int initialMaxFiltersPerCandidate;
    private final boolean prescreen;
    private final int prescreenFactor;
    private String lastRun;

    public GeneticSplitOptimizer(int blocks, int evaluationsBudget, int population, int survivors, int eliteCount, int generations, double mutationRate, long seed, int initialMaxFiltersPerCandidate, boolean prescreen, int prescreenFactor) {
        this.blocks = Math.max(1, blocks);
        this.evaluationsBudget = Math.max(1, evaluationsBudget);
        this.population = Math.max(2, population);
        this.survivors = Math.max(2, Math.min(survivors, this.population));
        this.eliteCount = Math.max(1, Math.min(eliteCount, this.survivors));
        this.generations = generations;
        this.mutationRate = Math.max(0.0, Math.min(1.0, mutationRate));
        this.seed = seed;
        this.initialMaxFiltersPerCandidate = Math.max(1, Math.min(FILTERS.size(), initialMaxFiltersPerCandidate));
        this.prescreen = prescreen;
        this.prescreenFactor = Math.max(1, prescreenFactor);
    }

    @Override public String name() { return "genetic"; }
    @Override public Optional<String> explainLastRun() { return Optional.ofNullable(lastRun); }

    @Override
    public FilteredImage optimize(RawImage image, CandidateGenerator candidates) {
        Random random = new Random(seed);
        var scorer = FastDeflateScorer.detected();
        List<List<FilteredRow>> rowCandidates = new ArrayList<>(image.height());
        for (int y = 0; y < image.height(); y++) rowCandidates.add(candidates.generateCandidates(image, y));

        int genCount = generations <= 0 ? autoGenerations() : generations;
        FitnessCache fitnessCache = new FitnessCache();

        List<Genome> seedPopulation = buildSeedPopulation(image, rowCandidates);
        List<Genome> pool = new ArrayList<>(seedPopulation);
        while (pool.size() < population) pool.add(randomGenome(random));
        if (pool.size() > population) pool = new ArrayList<>(pool.subList(0, population));

        List<Long> bestByGeneration = new ArrayList<>();
        ScoredGenome best = null;

        for (int generation = 0; generation < genCount && fitnessCache.evaluations < evaluationsBudget; generation++) {
            List<ScoredGenome> scored = scoreUnique(pool, image, rowCandidates, scorer, fitnessCache);
            if (scored.isEmpty()) break;
            scored.sort(Comparator.comparingLong(ScoredGenome::score));
            if (best == null || scored.get(0).score < best.score) best = scored.get(0);
            bestByGeneration.add(scored.get(0).score);

            List<Genome> parents = scored.stream().limit(survivors).map(ScoredGenome::genome).toList();
            List<Genome> next = new ArrayList<>();
            for (int i = 0; i < eliteCount && i < scored.size(); i++) next.add(scored.get(i).genome);

            int childrenTarget = Math.max(0, population - next.size());
            List<Genome> children = new ArrayList<>();
            int batch = prescreen ? childrenTarget * prescreenFactor : childrenTarget;
            for (int i = 0; i < batch; i++) {
                Genome a = parents.get(random.nextInt(parents.size()));
                Genome b = parents.get(random.nextInt(parents.size()));
                children.add(crossover(a, b, random).mutate(random, mutationRate));
            }
            if (prescreen && children.size() > childrenTarget) {
                children = children.stream()
                        .sorted(Comparator.comparingLong(g -> cheapScore(g, image, rowCandidates)))
                        .limit(childrenTarget)
                        .toList();
            } else if (children.size() > childrenTarget) {
                children = children.subList(0, childrenTarget);
            }
            next.addAll(children);
            pool = next;
        }

        if (best != null && fitnessCache.evaluations < evaluationsBudget) {
            best = localSearch(best, image, rowCandidates, scorer, fitnessCache);
        }

        Genome winner = best == null ? randomGenome(random) : best.genome;
        lastRun = buildLog(scorer.name, genCount, bestByGeneration, winner, fitnessCache);
        return new FilteredImage(image, materializeRows(image, rowCandidates, winner));
    }

    private int autoGenerations() {
        int childrenPerGeneration = Math.max(1, population - eliteCount);
        return Math.max(1, evaluationsBudget / childrenPerGeneration);
    }

    private ScoredGenome localSearch(ScoredGenome best, RawImage image, List<List<FilteredRow>> rowCandidates, FastDeflateScorer scorer, FitnessCache cache) {
        boolean improved = true;
        while (improved && cache.evaluations < evaluationsBudget) {
            improved = false;
            for (int b = 0; b < blocks && cache.evaluations < evaluationsBudget; b++) {
                PngFilter original = best.genome.blockFilters[b];
                for (PngFilter f : FILTERS) {
                    if (f == original || cache.evaluations >= evaluationsBudget) continue;
                    PngFilter[] next = Arrays.copyOf(best.genome.blockFilters, blocks);
                    next[b] = f;
                    ScoredGenome candidate = score(new Genome(next), image, rowCandidates, scorer, cache);
                    if (candidate.score <= best.score) {
                        best = candidate;
                        improved = true;
                    }
                }
            }
        }
        return best;
    }

    private List<ScoredGenome> scoreUnique(List<Genome> pool, RawImage image, List<List<FilteredRow>> rowCandidates, FastDeflateScorer scorer, FitnessCache cache) {
        List<ScoredGenome> out = new ArrayList<>();
        for (Genome g : pool) {
            ScoredGenome scored = score(g, image, rowCandidates, scorer, cache);
            if (scored != null) out.add(scored);
            if (cache.evaluations >= evaluationsBudget && !cache.map.containsKey(g.code())) break;
        }
        return out;
    }

    private ScoredGenome score(Genome genome, RawImage image, List<List<FilteredRow>> rowCandidates, FastDeflateScorer scorer, FitnessCache cache) {
        String key = genome.code();
        Long existing = cache.map.get(key);
        if (existing != null) {
            cache.cacheHits++;
            return new ScoredGenome(genome, existing);
        }
        if (cache.evaluations >= evaluationsBudget) return null;
        long score = scorer.score(toDeflateStream(image, rowCandidates, genome));
        cache.map.put(key, score);
        cache.evaluations++;
        return new ScoredGenome(genome, score);
    }

    private Genome crossover(Genome a, Genome b, Random random) {
        int mode = random.nextInt(4);
        return switch (mode) {
            case 0 -> a.onePoint(b, random);
            case 1 -> a.twoPoint(b, random);
            case 2 -> a.uniform(b, random);
            default -> a.rangeCopy(b, random);
        };
    }

    private long cheapScore(Genome genome, RawImage image, List<List<FilteredRow>> rowCandidates) {
        long s = 0;
        for (int y = 0; y < image.height(); y++) {
            int block = Math.min(blocks - 1, (y * blocks) / Math.max(1, image.height()));
            PngFilter f = genome.blockFilters[block];
            FilteredRow row = rowCandidates.get(y).stream().filter(c -> c.filter() == f).findFirst().orElse(rowCandidates.get(y).get(0));
            for (byte b : row.filteredBytes()) s += Math.abs((int) b);
        }
        return s;
    }

    private List<Genome> buildSeedPopulation(RawImage image, List<List<FilteredRow>> rowCandidates) {
        List<Genome> seeds = new ArrayList<>();
        for (PngFilter f : FILTERS) seeds.add(fillGenome(f));
        seeds.add(fromRows(image, rowCandidates, y -> rowCandidates.get(y).stream().min(Comparator.comparingLong(r -> entropyish(r.filteredBytes()))).orElse(rowCandidates.get(y).get(0)).filter()));
        seeds.add(fromRows(image, rowCandidates, y -> rowCandidates.get(y).stream().min(Comparator.comparingLong(r -> sumAbs(r.filteredBytes()))).orElse(rowCandidates.get(y).get(0)).filter()));
        seeds.add(fromRows(image, rowCandidates, y -> rowCandidates.get(y).get(0).filter()));
        List<Genome> mutated = new ArrayList<>();
        for (Genome s : seeds) mutated.add(s.flipOne(new Random(seed + s.code().hashCode())));
        seeds.addAll(mutated);
        return seeds;
    }

    private long entropyish(byte[] data) { long sum = 0; int[] hist = new int[256]; for (byte b: data) hist[b & 255]++; for (int h: hist) sum += (long) h * h; return sum; }
    private long sumAbs(byte[] data) { long sum = 0; for (byte b : data) sum += Math.abs((int)b); return sum; }

    private Genome fillGenome(PngFilter filter) { PngFilter[] gf = new PngFilter[blocks]; Arrays.fill(gf, filter); return new Genome(gf); }

    private Genome fromRows(RawImage image, List<List<FilteredRow>> perRow, java.util.function.IntFunction<PngFilter> fn) {
        PngFilter[] gf = new PngFilter[blocks];
        for (int b = 0; b < blocks; b++) gf[b] = PngFilter.NONE;
        for (int y = 0; y < image.height(); y++) {
            int block = Math.min(blocks - 1, (y * blocks) / Math.max(1, image.height()));
            gf[block] = fn.apply(y);
        }
        return new Genome(gf);
    }

    private Genome randomGenome(Random random) {
        PngFilter[] blockFilters = new PngFilter[blocks];
        List<PngFilter> shuffled = new ArrayList<>(FILTERS);
        Collections.shuffle(shuffled, random);
        List<PngFilter> allowed = shuffled.subList(0, Math.min(initialMaxFiltersPerCandidate, shuffled.size()));
        for (int b = 0; b < blocks; b++) blockFilters[b] = allowed.get(random.nextInt(allowed.size()));
        return new Genome(blockFilters);
    }

    private List<FilteredRow> materializeRows(RawImage image, List<List<FilteredRow>> perRow, Genome genome) { /* unchanged */
        List<FilteredRow> out = new ArrayList<>(image.height());
        for (int y = 0; y < image.height(); y++) {
            int block = Math.min(blocks - 1, (y * blocks) / Math.max(1, image.height()));
            PngFilter f = genome.blockFilters[block] == null ? PngFilter.NONE : genome.blockFilters[block];
            FilteredRow selected = perRow.get(y).stream().filter(c -> c.filter() == f).findFirst().orElse(perRow.get(y).get(0));
            out.add(selected);
        }
        return out;
    }

    private byte[] toDeflateStream(RawImage image, List<List<FilteredRow>> perRow, Genome genome) { /* unchanged */
        List<FilteredRow> rows = materializeRows(image, perRow, genome);
        int rowLen = image.width() * image.bytesPerPixel() + 1;
        byte[] out = new byte[rowLen * image.height()];
        int p = 0;
        for (FilteredRow row : rows) {
            out[p++] = (byte) row.filter().pngValue();
            System.arraycopy(row.filteredBytes(), 0, out, p, row.filteredBytes().length);
            p += row.filteredBytes().length;
        }
        return out;
    }

    private String buildLog(String scorerName, int generations, List<Long> bestByGeneration, Genome winner, FitnessCache cache) {
        StringBuilder b = new StringBuilder("Genetic optimizer:\n");
        b.append("- scorer: ").append(scorerName).append(" (fast-deflate estimated fitness)\n");
        b.append("- blocks: ").append(blocks).append("\n");
        b.append("- evaluations: ").append(cache.evaluations).append(" / ").append(evaluationsBudget).append("\n");
        b.append("- cache hits: ").append(cache.cacheHits).append("\n");
        b.append("- population: ").append(population).append("\n");
        b.append("- generations: ").append(generations).append("\n");
        b.append("- best fitness per generation:\n");
        for (int i = 0; i < bestByGeneration.size(); i++) b.append("  ").append(i).append(": ").append(bestByGeneration.get(i)).append("\n");
        b.append("- final genome: ").append(winner.code());
        return b.toString();
    }

    private static final class FitnessCache { final Map<String, Long> map = new HashMap<>(); int evaluations; int cacheHits; }

    private record Genome(PngFilter[] blockFilters) {
        Genome mutate(Random random, double mutationRate) { if (random.nextDouble() >= mutationRate) return this; return flipOne(random); }
        Genome flipOne(Random random) { PngFilter[] child = Arrays.copyOf(blockFilters, blockFilters.length); child[random.nextInt(child.length)] = FILTERS.get(random.nextInt(FILTERS.size())); return new Genome(child); }
        Genome onePoint(Genome o, Random r) { PngFilter[] c = Arrays.copyOf(blockFilters, blockFilters.length); int p = r.nextInt(blockFilters.length); for (int i = p; i < c.length; i++) c[i] = o.blockFilters[i]; return new Genome(c); }
        Genome twoPoint(Genome o, Random r) { PngFilter[] c = Arrays.copyOf(blockFilters, blockFilters.length); int a = r.nextInt(c.length); int b = r.nextInt(c.length); if (a>b){int t=a;a=b;b=t;} for (int i=a;i<=b;i++) c[i]=o.blockFilters[i]; return new Genome(c); }
        Genome uniform(Genome o, Random r) { PngFilter[] c = Arrays.copyOf(blockFilters, blockFilters.length); for (int i=0;i<c.length;i++) if (r.nextBoolean()) c[i]=o.blockFilters[i]; return new Genome(c); }
        Genome rangeCopy(Genome o, Random r) { return twoPoint(o,r); }
        String code() { StringBuilder b = new StringBuilder(blockFilters.length); for (PngFilter f : blockFilters) b.append(switch (f == null ? PngFilter.NONE : f) { case NONE -> 'N'; case SUB -> 'S'; case UP -> 'U'; case AVERAGE -> 'A'; case PAETH -> 'P';}); return b.toString(); }
    }
    private record ScoredGenome(Genome genome, long score) {}

    private static final class FastDeflateScorer { /* unchanged */
        private static volatile FastDeflateScorer cached;
        private final String name; private final List<String> cmd;
        private FastDeflateScorer(String name, List<String> cmd) { this.name = name; this.cmd = cmd; }
        static FastDeflateScorer detected() { FastDeflateScorer local = cached; if (local != null) return local; synchronized (FastDeflateScorer.class) { if (cached == null) cached = autoDetect(); return cached; } }
        private static FastDeflateScorer autoDetect() { List<FastDeflateScorer> options = List.of(new FastDeflateScorer("pigz-1", List.of("pigz", "-1", "-c")), new FastDeflateScorer("gzip-1", List.of("gzip", "-1", "-c")), new FastDeflateScorer("zlib-java-1", List.of())); for (FastDeflateScorer s : options) { if (s.cmd.isEmpty()) return s; try { Process p = new ProcessBuilder(s.cmd).start(); p.destroyForcibly(); return s; } catch (IOException ignored) {} } return options.get(options.size() - 1); }
        long score(byte[] input) { if (cmd.isEmpty()) return zlibLen(input); try { Path in = Files.createTempFile("png-ga", ".bin"); Files.write(in, input); Process p = new ProcessBuilder(cmd).redirectInput(in.toFile()).start(); byte[] out = p.getInputStream().readAllBytes(); int exit = p.waitFor(); Files.deleteIfExists(in); if (exit == 0) return out.length; } catch (Exception ignored) {} return zlibLen(input); }
        private static int zlibLen(byte[] input) { Deflater d = new Deflater(1, true); d.setInput(input); d.finish(); byte[] buf = new byte[8192]; ByteArrayOutputStream compressed = new ByteArrayOutputStream(Math.max(1024, input.length / 2)); while (!d.finished()) { int n = d.deflate(buf); if (n > 0) compressed.write(buf, 0, n);} d.end(); return compressed.size(); }
    }
}
