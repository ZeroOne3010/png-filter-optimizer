# pngfilteropt

Experimental CLI for PNG scanline filter optimization.

`pngfilteropt` explores whether smarter per-row filter selection can reduce final PNG size, especially after optional `zopflipng --filters=p` recompression.

## What it does

Pipeline:

1. Decode PNG rows
2. Generate candidate filter streams (`NONE`, `SUB`, `UP`, `AVERAGE`, `PAETH`)
3. Choose filters with pluggable optimizers
4. Write optimized PNG
5. Optionally run `zopflipng` and keep the smallest result

## Implemented commands

- `optimize input.png output.png` — optimize one image
- `inspect input.png` — print row/filter mapping
- `benchmark [dir]` — run strategies on all `*.png` files recursively and emit markdown/json summaries

## Valid CLI optimizer names (`--optimizer`)

- `baseline` — preserves current filter strategy and only recompresses.
- `entropy` — chooses per-row filters by Shannon entropy heuristics.
- `adaptive` — greedy per-row sum-of-absolute-values heuristic (fast, not true global DEFLATE optimization).
- `exhaustive` — beam search over row filter sequences scored by LZ-style heuristics (`--beam` controls search width).
- `genetic` — genetic scanline-block search scored by a fast DEFLATE-size heuristic (`--genetic-*` / `--ga-*` flags).
- `fixed-none` — forces filter `0` (`NONE`) for every row.
- `fixed-sub` — forces filter `1` (`SUB`) for every row.
- `fixed-up` — forces filter `2` (`UP`) for every row.
- `fixed-average` — forces filter `3` (`AVERAGE`) for every row.
- `fixed-paeth` — forces filter `4` (`PAETH`) for every row.

## Benchmark behavior

- By default, `benchmark` runs only `adaptive` unless you pass `--optimizer` or `--try-all`.
- `--try-all` runs all optimizer names listed above.
- Benchmark output always includes control/reference columns such as `original` and `rewritten-baseline`.
- If `--zopflipng` is provided and benchmark controls are enabled, benchmark can include:
  - `zopflipng-default-original`
  - `zopflipng-preserve-original-filters`
  - `rewritten+zopfli-preserve`


## Benchmark explanations and insights

`benchmark` always writes the maximal report. The summary table links each image name to a localized image section containing PNG metadata, per-strategy size and timing results, ratios against the winning strategy, filter distributions, compression explanations, verbose insights, collapsible diagnostic tables, and filter-layout previews.

## Filter layout visualizations

Benchmark reports include a machine-readable `filter_layouts` JSON object for every image/strategy. Each layout records per-row filter names plus run-length ranges (`start_row`, `end_row`, `row_count`, `filter`) so exact filter placement can be inspected or reused by other tools.

For every non-trivial layout (more than one contiguous filter run), `benchmark` also writes a small palettized PNG preview under `filter-visualizations/` next to the markdown report. The preview is scaled so neither side exceeds 256 pixels by default and tints source rows by filter: `NONE` red, `SUB` orange, `UP` blue, `AVERAGE` green, and `PAETH` purple. Vertical downscaling preserves single-row filter runs whenever the number of runs fits in the reduced height, so isolated filter rows do not disappear just because the preview is smaller.

The provided benchmark GitHub Action publishes previews through a GitHub Pages artifact under a path containing the current workflow run ID and rewrites report image references to the resulting Pages URLs. Each Pages deployment contains only the current run's image paths, so older PR comments do not accidentally show images from newer runs after a later deployment replaces the site. The repository's Pages source must be configured for GitHub Actions deployments.

## Quick examples

```bash
./gradlew run --args="optimize in.png out.png --optimizer entropy"
./gradlew run --args="optimize in.png out.png --optimizer adaptive"
./gradlew run --args="optimize in.png out.png --optimizer genetic --genetic-evaluations 512"
./gradlew run --args="optimize in.png out.png --try-all --zopflipng zopflipng"
./gradlew run --args="inspect in.png"
./gradlew run --args="benchmark src/test/resources/test-images --markdown build/reports/pngfilteropt/benchmark.md --json build/reports/pngfilteropt/benchmark.json"
```

## Accuracy note

- Current optimizers are heuristic proxies for eventual DEFLATE size.
- `adaptive`, `exhaustive`, and `genetic` are not mathematically exact global minima.
- For exact minimization claims, a true end-to-end global DEFLATE objective would need to be implemented.

## How the genetic optimizer works

`genetic` does **block-wise** search, not full per-row brute force:

1. **Genome representation**  
   The image height is split into `--genetic-blocks` segments. A candidate solution (genome) stores one PNG filter (`NONE/SUB/UP/AVERAGE/PAETH`) per block.

2. **Row candidates are precomputed once**  
   For every row, all filter variants are generated. A genome only chooses *which* precomputed variant each row should use (via its block’s filter).

3. **Fitness = estimated compressed size**  
   Each genome is materialized into a PNG scanline byte stream and scored by fast level-1 DEFLATE (`pigz -1`, `gzip -1`, or Java zlib fallback). Lower compressed length is better.

4. **Initial population (seeded + random)**  
   The population starts with useful seeds (all-fixed filters and entropy/adaptive/baseline-derived block assignments), then fills with random genomes.

5. **Evolution loop**  
   Per generation, genomes are scored, best ones survive, elites are copied unchanged, and children are created via crossover (one-point, two-point, uniform) plus mutation (random single-block flip with probability `--genetic-mutation`).

6. **Optional child prescreening**  
   If enabled, extra children are generated and cheaply ranked using a sum-of-absolute-residual proxy; only the best subset gets expensive DEFLATE scoring.

7. **Evaluation-budget bounded**  
   Search stops at `--genetic-evaluations` scored genomes (or configured generation limit). A score cache avoids re-evaluating duplicate genomes.

8. **Final local improvement**  
   A hill-climb pass tries swapping each block to every filter and keeps strictly improving changes until no improvement remains or budget is exhausted.

In short: it explores coarse filter layouts globally with GA operators, uses fast compression as the objective, and spends a fixed scoring budget where it matters most.
