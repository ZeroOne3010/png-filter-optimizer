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

## Optimizers currently in the codebase

- `fixed` — forces the same PNG filter for every scanline.
- `sumabs` — picks per-row filters that minimize the sum of absolute filtered byte values.
- `entropy` — picks per-row filters with the lowest byte entropy as a compression proxy.
- `runs` — favors filters that produce longer repeated-byte runs in filtered output.
- `lz-greedy` — uses a greedy LZ-style match estimator to select filters row by row.
- `lz-beam` — uses beam search over LZ-estimated costs to keep several promising filter paths.

## Quick examples

```bash
./gradlew run --args="optimize in.png out.png --optimizer sumabs"
./gradlew run --args="optimize in.png out.png --try-all --zopflipng zopflipng"
./gradlew run --args="inspect in.png"
./gradlew run --args="benchmark src/test/resources/test-images --markdown build/reports/pngfilteropt/benchmark.md --json build/reports/pngfilteropt/benchmark.json"
```
