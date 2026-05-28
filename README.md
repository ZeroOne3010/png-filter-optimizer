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
