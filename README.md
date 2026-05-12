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
- `benchmark <dir>` — run strategies on all `*.png` files recursively and emit markdown/json summaries

## Valid CLI optimizer names (`--optimizer`)

- `baseline` — preserves the baseline filtering approach and recompresses output.
- `entropy` — chooses per-row filters using Shannon entropy heuristics.
- `adaptive` — uses a dynamic-programming style global optimization strategy.
- `exhaustive` — searches filter combinations up to the configured `--beam` width.
- `literal` — always uses filter `0` (`NONE`) for every row (no prediction).

## Benchmark behavior

- By default, `benchmark` runs only `adaptive` unless you pass `--optimizer` or `--try-all`.
- `fixed-none` is always included in benchmark output as a reference column.
- `--try-all` runs all optimizer names: `baseline`, `entropy`, `adaptive`, `exhaustive`, `literal`.

## Quick examples

```bash
./gradlew run --args="optimize in.png out.png --optimizer entropy"
./gradlew run --args="optimize in.png out.png --optimizer adaptive"
./gradlew run --args="optimize in.png out.png --try-all --zopflipng zopflipng"
./gradlew run --args="inspect in.png"
./gradlew run --args="benchmark src/test/resources/test-images --markdown build/reports/pngfilteropt/benchmark.md --json build/reports/pngfilteropt/benchmark.json"
```
