# 05 — Map of every file

A room-by-room tour. Paths are relative to the repo root.

## The library itself — `src/commonMain/kotlin/io/github/likhithsj/kmatch/`

Eleven files. "Common" means this exact code compiles for JVM, iOS, JS, Wasm,
Windows, Linux — everything. There is deliberately zero platform-specific
code.

| File | Floor | What it owns |
|---|---|---|
| `CodePoints.kt` | 1 | Text ↔ integer-array conversion (`toCodePoints`). The reason emoji and all scripts work correctly. |
| `Indel.kt` | 1 | **The engine.** `IndelPattern` (the bulb masks), the single-Long and blocked LCS loops, distance/similarity + cutoff early-exits. |
| `UnicodeTables.kt` | 1½ | Generated lookup tables: which characters are letters/digits/whitespace, and lowercase mappings — probed from RapidFuzz itself. Don't edit by hand; regenerate with the tool. |
| `Process.kt` | 2 | `defaultProcess` — lowercase, de-punctuate, trim. The standard pre-cleaning. |
| `Fold.kt` | 2 | `stripAccents` / `searchFold` — the opt-in accent fold ("jalapeno" finds "Jalapeño"). kmatch's own feature, outside RapidFuzz parity. |
| `FoldTables.kt` | 1½ | Generated NFKD fold table behind `stripAccents`. Regenerate with `tools/generate_fold_tables.py`. |
| `Fuzz.kt` | 2 | All ten public scorers (`ratio` … `weightedRatio`) and their internal `...Cps` implementations that mirror RapidFuzz step for step. The biggest file; organized top (public wrappers) to bottom (internals). |
| `CachedScorer.kt` | 2½ | `prepareScorer` — the "build the query's masks once, reuse across a scan" machinery that extraction uses behind the scenes. |
| `Extract.kt` | 3 | The search API: `extractOne/Top/Sorted/All` (string and generic-`T` flavors), the `Scorer` interface, `Scorers` presets, result types. |
| `Dedupe.kt` | 3 | `dedupe` — collapse near-duplicate entries, keeping one representative per group. |
| `Align.kt` | 3 | `matchingBlocks` / `matchingRanges` — recover *which characters* matched, for highlighting in UIs. Uses a divide-and-conquer method (Hirschberg) so long strings don't eat memory. |

**Jump straight to the landmark of each file:**
[`toCodePoints` — CodePoints.kt:10](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/CodePoints.kt#L10) ·
[`IndelPattern` — Indel.kt:38](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Indel.kt#L38) ·
[`lcsSingle` — Indel.kt:102](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Indel.kt#L102) ·
[`defaultProcess` — Process.kt:16](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Process.kt#L16) ·
[`Fuzz.ratio` — Fuzz.kt:29](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Fuzz.kt#L29) ·
[`weightedRatioCps` — Fuzz.kt:445](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Fuzz.kt#L445) ·
[`prepareScorer` — CachedScorer.kt:23](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/CachedScorer.kt#L23) ·
[`Scorer` — Extract.kt:11](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Extract.kt#L11) ·
[`extractOne` (generic) — Extract.kt:64](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Extract.kt#L64) ·
[`dedupe` — Dedupe.kt:22](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Dedupe.kt#L22) ·
[`matchingBlocks` — Align.kt:38](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Align.kt#L38) ·
[`hirschberg` — Align.kt:89](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Align.kt#L89)

## The tests — `src/commonTest/` (+ `src/jvmTest/`)

| File | What it proves |
|---|---|
| `GoldenVectors.kt` | **Generated, 3,260 cases.** Inputs + the exact score real RapidFuzz produced. The parity contract, executable. |
| `GoldenVectorTest.kt` | Runs all vectors, demands bit-exact equality — scores AND cutoff behavior. |
| `BitParallelTest.kt` | Bulb method ≡ textbook grid on thousands of random inputs (the slow grid lives in `TestReference.kt` as ground truth). |
| `CachedScorerTest.kt` | The mask-reusing fast path returns exactly what the plain path returns. |
| `ExtractTest.kt`, `GenericExtractTest.kt`, `DedupeTest.kt`, `AlignTest.kt`, `FuzzTest.kt` | Behavior of each floor-3 feature, including the edge cases that once were bugs (empty keys, astral-plane tie-breaks…). |
| `src/jvmTest/.../BenchmarkTest.kt` | The speed numbers, reproducible: set `KMATCH_BENCH=1`. Includes head-to-head vs two other libraries. |

## The generators — `tools/`

| File | Run when |
|---|---|
| `generate_vectors.py` | RapidFuzz is pinned at 3.14.5; this re-asks it all 3,260 questions and rewrites `GoldenVectors.kt`. CI runs it on every push and fails if the committed file differs ("drift check"). |
| `generate_unicode_tables.py` | Same idea for `UnicodeTables.kt`. |
| `generate_fold_tables.py` | Generates `FoldTables.kt` (the `stripAccents` fold). Pinned to Python 3.12's Unicode tables; not part of the CI drift check because it has no RapidFuzz reference — rerun it only when the fold rules change. |

## The demo — `demo/`

The playground website (https://likhithsj.github.io/kmatch/): the library
compiled to JavaScript, plus one HTML page. `src/jsMain/kotlin/Main.kt` has
the `fun main()` that wires input boxes to library calls.

## Worked examples — `samples/`

| Folder | Shows |
|---|---|
| `menu-search/` | Integrating with an app-shaped model: category tree + keyword map, one `extractTop` call, and a runnable old-vs-new comparison table. **Start here if you're integrating.** |
| `migrate-fuzzywuzzy/` | Replacing me.xdrop:fuzzywuzzy behind the same interactor signature: the string-round-trip identity bug demonstrated, the typed drop-in, a side-by-side table, pinned tests, and a migration checklist. |
| `consumer-check/` | A consumer of the *published* artifact, used by CI to verify 14 environments (the evidence behind `VERIFICATION.md`). |
| `consumer-check-android/` | Same proof from an Android (AGP) module. |
| `ios-skie/` | Calling kmatch from **Swift** through SKIE, with assertions. The iOS team's starting point. |

## The automation — `.github/workflows/`

| Workflow | Fires on | Does |
|---|---|---|
| `ci.yml` | every push | Tests on Linux targets + full Apple matrix + golden-vector drift check |
| `docs.yml` | every push to main | Rebuilds and deploys the playground + API docs site |
| `release.yml` | tag `v*` | Full test matrix, then sign + publish every target to Maven Central |
| `verify-consumers.yml` | changes to samples / manual | The 14-environment consumer matrix |
| `skie-sample.yml` | changes to the sample / manual | The Swift/SKIE verification |

## Top-level papers

- `README.md` — the pitch + usage; `VERIFICATION.md` — the evidence table;
  `RELEASING.md` — how a release is cut; `LICENSE` — MIT;
  `gradle.properties` — version + publishing metadata (POM).

**Next:** [06-from-push-to-your-phone.md](06-from-push-to-your-phone.md) —
the life of a change, from `git push` to running inside an app.
