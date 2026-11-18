# kmatch

[![Maven Central](https://img.shields.io/maven-central/v/io.github.likhithsj/kmatch)](https://central.sonatype.com/artifact/io.github.likhithsj/kmatch)
[![CI](https://github.com/likhithsj/kmatch/actions/workflows/ci.yml/badge.svg)](https://github.com/likhithsj/kmatch/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Fast, RapidFuzz-compatible fuzzy string matching for Kotlin Multiplatform.

- **Verified parity** — bit-exact with [RapidFuzz](https://github.com/rapidfuzz/RapidFuzz)'s C++ backend, checked against 4,380 golden vectors (scores and `scoreCutoff` behavior, down to cutoffs placed exactly on a score) on every commit, plus a [differential fuzzer](tools/differential-fuzz/) that compares random inputs against the real thing. Tune thresholds in a Python notebook, ship the numbers to Kotlin unchanged.
- **Bit-parallel core** — Hyyrö's bit-parallel LCS packs the DP column into machine words; extraction prepares the query once per scan (for the `ratio`-family presets that includes its match masks, reused for every choice) and short-circuits on `scoreCutoff`.
- **Correct on full Unicode** — all algorithms operate on code points, never UTF-16 chars. A single emoji is one symbol, astral-plane characters sort correctly, and `defaultProcess` uses the exact Unicode tables of RapidFuzz's C++ backend.
- **19 KMP targets, one dependency line** — JVM (serves Android), JS, Wasm, iOS (including `iosX64`), macOS, watchOS, tvOS, Linux, Windows, Android Native.
- **Zero runtime dependencies**, MIT licensed.

> **Name note:** several unrelated projects are also called "kmatch"/"KMatcher" (including a Kotlin pattern-matching DSL). This library is fuzzy *string* matching, has no DSL, and lives at the Maven coordinate `io.github.likhithsj:kmatch` — when in doubt, that coordinate is the identity.

## Try it

**[Live playground →](https://likhithsj.github.io/kmatch/)** — compare strings across every scorer, search a collection with match highlighting, flip the accent fold on and off, and play with category boosts (raw vs boosted ranking, side by side), running the real library as Kotlin/JS in your browser. **[API docs →](https://likhithsj.github.io/kmatch/api/)** (Source in [`demo/`](demo/).)

New to the codebase? **[docs/learn/](docs/learn/)** is a seven-part guided tour — where execution starts, the journey of a call through every file, the scoring math, the bit-parallel trick, a map of every file, how a push becomes a published release, and how categories and priorities work around the library.

## Install

```kotlin
implementation("io.github.likhithsj:kmatch:1.0.0")
```

One dependency line for all 19 targets: JVM/Android, iOS (device + both simulators), macOS, watchOS, tvOS, JS, Wasm, Linux, Windows, Android Native — Gradle selects the right artifact automatically. The JVM artifact targets Java 8 bytecode, so it runs on any JVM from 8 up. Consuming from Swift? See the [SKIE sample](samples/ios-skie/).

1.0.0 is the current release. Two RapidFuzz-parity fixes made since are not released yet; see [Roadmap](#roadmap) for what they change.

Want to see it inside a real app? [`samples/starbucks-menu/`](samples/starbucks-menu/) is an Android + iOS app that pulls a real menu from the web and searches it with kmatch. All samples are listed [below](#samples).

## Scorers

All scorers return a `Double` on RapidFuzz's 0–100 scale and match its output exactly:

```kotlin
import io.github.likhithsj.kmatch.Fuzz
import io.github.likhithsj.kmatch.defaultProcess

Fuzz.ratio("this is a test", "this is a test!")        // 96.55172413793103
Fuzz.partialRatio("this is a test", "this is a test!") // 100.0
Fuzz.tokenSortRatio("fuzzy wuzzy was a bear", "wuzzy fuzzy was a bear") // 100.0
Fuzz.tokenSetRatio("fuzzy was a bear but not a dog", "fuzzy was a bear") // 100.0
Fuzz.weightedRatio("this is a test", "this is a new test!!")  // WRatio
Fuzz.quickRatio("this is a test", "this is a new test!!")     // QRatio

// RapidFuzz's default preprocessing: lowercase, non-alphanumeric -> space, trim.
Fuzz.ratio("Test String!", "test string", processor = ::defaultProcess) // 100.0

// scoreCutoff: results below the cutoff return 0.0.
Fuzz.ratio("hello world", "hello world!", scoreCutoff = 99.0) // 0.0
```

Full surface: `ratio`, `partialRatio`, `tokenSortRatio`, `tokenSetRatio`, `tokenRatio`, `partialTokenSortRatio`, `partialTokenSetRatio`, `partialTokenRatio`, `weightedRatio` (WRatio), `quickRatio` (QRatio), `defaultProcess`.

`scoreCutoff` is inclusive: a result equal to the cutoff is returned, anything below it comes back as 0.0. So a score can be fed back in as the cutoff ("everything at least as good as this") and the match survives.

Note that `ratio` is indel-based (insertions and deletions only), **not** normalized Levenshtein: `ratio("myself", "me")` is 50 under indel, 33.3 under Levenshtein. This matches RapidFuzz and fuzzywuzzy semantics and is the single most common porting error.

## Extraction

```kotlin
import io.github.likhithsj.kmatch.*

val choices = listOf("Atlanta Falcons", "New York Jets", "New York Giants", "Dallas Cowboys")

extractOne("new york jets", choices, processor = ::defaultProcess)
// ExtractedResult(choice="New York Jets", score=100.0, index=1)

extractTop("new york", choices, limit = 2, processor = ::defaultProcess)
extractSorted("new york", choices, scoreCutoff = 50.0)
extractAll("new york", choices)

// Custom scorers are a fun interface:
val exact = Scorer { a, b, _ -> if (a == b) 100.0 else 0.0 }
extractOne("query", choices, scorer = exact)
```

Every `extract*` function scores with `Scorers.WeightedRatio` unless told otherwise, and `extractTop` returns 5 results by default. The `Scorers` object holds a ready-made preset for each of the ten `Fuzz` functions:

```kotlin
extractTop("new york", choices, scorer = Scorers.TokenSetRatio, limit = 3)
```

Pass the preset itself rather than a lambda that calls `Fuzz.tokenSetRatio`: extraction recognizes the presets and prepares the query once for the whole scan, while a custom `Scorer` is called with plain strings for every choice. Results are `ExtractedResult(choice, score, index)` for strings and `ExtractedItem<T>(item, score, index)` for records, where `index` is the position in the input. `extractSorted` and `extractTop` keep input order among equal scores, and `extractOne` returns the first of the best, as RapidFuzz does.

Extraction is generic — score your records directly with a `keySelector` and get the records back:

```kotlin
data class City(val name: String, val population: Int)

extractOne("new york", cities, keySelector = { it.name }, processor = ::defaultProcess)
// ExtractedItem(item=City(name=New York, population=8300000), score=100.0, index=0)
```

Items with several searchable fields (display name, alias keywords, category) score best with `keySelectors` — each field is scored independently and the item takes its **best** field:

```kotlin
extractTop("doppio", products,
    keySelectors = listOf({ it.name }, { it.keywords.joinToString(" ") }),
    processor = ::searchFold)
// Espresso (keywords "shot doppio") wins at 90.0: a full partial match in the
// keyword field, x 0.9. The non-matching name could not dilute it.
```

All four functions (`extractOne`, `extractTop`, `extractSorted`, `extractAll`) have the `keySelectors` form. Prefer it over concatenating fields into one key: concatenation makes a strong hit in one short field compete with the combined length of all of them (`weightedRatio` down-scales a match once the key grows past 8× the query length), while per-field scoring keeps "best alias wins" semantics.

## Dedupe and match highlighting

```kotlin
// Collapse near-duplicates (fuzzywuzzy process.dedupe semantics: each
// duplicate group keeps its longest entry).
dedupe(listOf("Frodo Baggins", "Frodo Baggin", "F. Baggins", "Gandalf"))
// [Frodo Baggins, Gandalf]

// Highlight why a choice matched: char-index ranges into the choice string.
matchingRanges("new york", "the new york times")   // ranges covering "new york"
matchingBlocks("kitten", "sitting")                // aligned blocks in both strings
```

`dedupe` defaults to `threshold = 70.0` and `Scorers.TokenSetRatio` (so reordered words count as duplicates), and has a generic form that returns your records: `dedupe(products, keySelector = { it.name })`. It compares every pair of items, so cost grows with the square of the list: fine for hundreds or a few thousand entries, not for millions.

`matchingBlocks` returns `MatchingBlock(aStart, bStart, length)` and `matchingRanges` returns `IntRange`s into the choice. Both recover one optimal indel alignment with Hirschberg's algorithm — linear memory, so long inputs are safe. Indices are UTF-16 char units, directly usable with `substring` or `AnnotatedString` (astral-plane characters handled correctly).

## Accent-insensitive search

Users type `caffe latte` and `jalapeno`; menus say `Caffè Latte` and `Jalapeño`. The built-in `searchFold` processor makes those exact matches:

```kotlin
extractTop("jalapeno bagel", items, keySelector = { it.name }, processor = ::searchFold)
Fuzz.ratio("caffe latte", "Caffè Latte", processor = ::searchFold)   // 100.0

stripAccents("Łódź")        // "Lodz" — the fold alone, case preserved
searchFold("  Caffè! ")     // "caffe" — fold + defaultProcess
```

The fold is deliberately conservative: NFKD plus mark-stripping only for scripts where marks are optional (Latin/Greek/Cyrillic accents, Hebrew points, Arabic harakat), explicit folds for non-decomposable Latin letters (`ø`→`o`, `æ`→`ae`, `ß`→`ss`, `ł`→`l`, …), and compatibility forms (full-width `Ｔｏｋｙｏ`→`Tokyo`, ligature `ﬁ`→`fi`). Indic and Southeast Asian marks are left intact (they are vowels), Hangul stays composed. This is kmatch's own opt-in processor — RapidFuzz parity is untouched because no scorer folds unless you pass it. The fold table is generated (`tools/generate_fold_tables.py`, pinned to Unicode 15.0.0) and compiled into the library, so every target folds identically instead of deferring to its platform's Unicode APIs.

## How parity is verified

`tools/generate_vectors.py` pins RapidFuzz 3.14.5 and emits `GoldenVectors.kt` — 4,380 (inputs, scorer, expected score) cases: 1,680 plain scores and 2,700 `scoreCutoff` cases. They cover plain ASCII, accented Latin, astral-plane characters, empty strings, strings past 64 code points, token edge cases, non-Latin scripts through `defaultProcess`, token separators that depend on the string, and cutoffs placed exactly on a score and one floating-point step either side of it. CI asserts **exact** float64 equality on every tested target and regenerates the vectors to catch drift.

Fixed vectors only pin cases someone thought of. [`tools/differential-fuzz`](tools/differential-fuzz/) covers the rest: random inputs, answered by RapidFuzz and by kmatch, compared bit for bit, about 254,000 comparisons in a default run. Its first run found two parity bugs in 1.0.0 (see [Roadmap](#roadmap)); the current tree is clean over a million comparisons across two seeds.

`defaultProcess` and tokenization use Unicode tables probed directly from RapidFuzz's C++ backend (`tools/generate_unicode_tables.py`) rather than any host platform's Unicode APIs. The backends genuinely differ from Python's `re` module, from Java's `Character`, and from RapidFuzz's own pure-Python fallback: underscore handling, simple vs. full case mapping, and which characters separate tokens. One example kmatch reproduces: in the C++ backend a no-break space (U+00A0) separates tokens only in strings that contain a character above U+00FF, so `"Łódź\u00A0Kaliska"` is two tokens and `"Zürich\u00A0HB"` is one. The probe-derived tables make kmatch match what `pip install rapidfuzz` users actually observe.

[VERIFICATION.md](VERIFICATION.md) holds the consumer-side evidence (the published artifact, on every environment CI can execute) and a ten-minute recipe for re-checking the parity claim yourself.

## Performance

The Hyyrö bit-parallel LCS replaces the 0.1.0 code-point DP behind the same frozen API — every output is proven unchanged by the golden vectors plus randomized equivalence tests against the DP (kept in test sources as the reference).

Where the speed shows up, measured by the in-repo benchmark (`KMATCH_BENCH=1 ./gradlew jvmTest --tests '*BenchmarkTest' --rerun`, JVM 21, median of 7 rounds):

| Workload | Baseline | Now | Speedup |
|---|---|---|---|
| `extractOne`, 20k choices, `Scorers.Ratio` | 66 ms (string path: no query preparation) | 4.6 ms | **~14×** |
| ...with `scoreCutoff = 80` | 66 ms (same) | 4.0 ms | **~16×** |
| LCS core of `ratio`, ~370 code points | 51 ms / 300 pairs (0.1.0 DP) | 13 ms | **~4×** |
| LCS core of `ratio`, ~36 code points | 8.3 ms / 2000 pairs (0.1.0 DP) | 10.2 ms | ~0.8× |

Collection scans win big because the query's match masks are built once and reused for every choice, and `scoreCutoff` aborts comparisons whose length difference already rules them out. Mask reuse applies to `Scorers.Ratio`, `QuickRatio`, `TokenSortRatio`, `PartialRatio` and `PartialTokenSortRatio`; the other presets, including the default `WeightedRatio`, convert the query once but build masks per comparison. One-shot calls on short string pairs are microsecond-scale either way; if you score one query against many strings, use the `extract*` functions rather than looping over `Fuzz.ratio` yourself. Numbers are indicative (container hardware), and the harness is committed so you can reproduce them on yours.

### vs other JVM/Kotlin libraries

Same harness (`thirdPartyComparison`), same data, each library's own API; 2,000 pairs / 20,000-choice scan, JVM 21:

| Workload | kmatch | [me.xdrop:fuzzywuzzy](https://github.com/xdrop/fuzzywuzzy) 1.4.0 | [kt-fuzzy](https://solo-studios.ca/) 0.1.0 |
|---|---|---|---|
| `ratio`, 2k pairs | **1.8 ms** | 15.2 ms (8.4× slower) | 39.3 ms (22× slower) |
| `partialRatio`, 2k pairs | **20.9 ms** | 172.9 ms (8.3× slower) | 35.8 ms (1.7× slower) |
| `extractOne`, 20k choices (WRatio) | **66.9 ms** | 801.2 ms (12× slower) | — no extraction API |

Throughput comparison only — output scales and rounding differ between libraries (kmatch is the one matching RapidFuzz bit-exactly). Both libraries are JVM-test-only dependencies of the benchmark; the shipped artifact stays zero-dependency.

## Roadmap

- ✅ **0.1.0–0.2.0** (development milestones, never published) — parity core, then the bit-parallel performance core.
- ✅ **0.3.0** (first release on Maven Central) — generic extraction, `dedupe`, `matchingRanges`, published benchmarks, playground, API docs.
- ✅ **0.3.1** (released) — JVM artifact targets Java 8 bytecode, so it loads on any JVM from 8 up.
- ✅ **1.0.0** (released, current) — API freeze, closing the early-adopter feedback round: built-in accent folding (`searchFold`, `stripAccents`) and multi-field extraction (`keySelectors`, best field wins) shipped. The prepared-query cache stays internal (extraction uses it automatically) and lazy `Sequence` extraction is deferred — both can be added later without breaking anything, which is the test an API freeze has to pass.
- 🔧 **Unreleased (1.0.1 candidate)** — two RapidFuzz parity fixes found by differential fuzzing. No API change; scores move only in these two situations, toward RapidFuzz:
  - `partialRatio` and everything built on it (`partialTokenSortRatio`, `partialTokenSetRatio`, `partialTokenRatio`, `weightedRatio`, and extraction with them) could return 0.0 when `scoreCutoff` was within one floating-point step of the true score — the true score itself included. It depended on how the particular score rounds, so most calls were unaffected. `Fuzz.partialRatio("abbabbaabba", "bababa")` is 83.33333333333334; passing that back as the cutoff returned 0.0 in 1.0.0 and returns the score now.
  - Token scorers on unprocessed input did not split on NBSP (U+00A0) or NEL (U+0085) in strings containing a character above U+00FF. `tokenSortRatio("Łódź\u00A0Kaliska", "Kaliska Łódź")` was 58.3 in 1.0.0 and is 100.0 now, as in RapidFuzz. Input that goes through `defaultProcess` or `searchFold` was never affected.
- **Next (1.x candidates, additive only)** — a public prepared-query API, lazy `Sequence` extraction, weighted multi-field scoring. Driven by adopter feedback: [open an issue](https://github.com/likhithsj/kmatch/issues).

## Samples

| Folder | Shows |
|---|---|
| [`samples/starbucks-menu`](samples/starbucks-menu/) | A complete Compose Multiplatform app for Android and iOS. Pulls the Starbucks® menu from the web (with a bundled snapshot for offline start) and searches ~310 products with typo tolerance, accent folding, nicknames, and a soft category boost. Shows per-key-type weights and a second scorer for nicknames on top of `extractAll`. |
| [`samples/menu-search`](samples/menu-search/) | Integrating with an app-shaped model: category tree + keyword aliases, one `extractTop` call, holiday-boost ranking, old-vs-new comparison tests. **Start here if you're integrating.** |
| [`samples/migrate-fuzzywuzzy`](samples/migrate-fuzzywuzzy/) | Migrating off `me.xdrop:fuzzywuzzy` behind the same interactor signature, with the identity-bug evidence and a migration checklist. |
| [`samples/ios-skie`](samples/ios-skie/) | Calling kmatch from Swift through SKIE, with assertions. |
| [`samples/consumer-check`](samples/consumer-check/) + [`-android`](samples/consumer-check-android/) | The consumers of the *published* artifact behind [VERIFICATION.md](VERIFICATION.md): tests executed on 13 environments, compile checks on four more targets. |

## More documentation

- [docs/learn/](docs/learn/) — the seven-part guided tour of the codebase.
- [VERIFICATION.md](VERIFICATION.md) — consumer-side evidence and how to re-check parity yourself.
- [tools/differential-fuzz/](tools/differential-fuzz/) — the fuzzer that compares kmatch with RapidFuzz on random inputs.
- [RELEASING.md](RELEASING.md) — how a version gets to Maven Central.
- [API reference](https://likhithsj.github.io/kmatch/api/) and the [playground](https://likhithsj.github.io/kmatch/).

## License

[MIT](LICENSE)
