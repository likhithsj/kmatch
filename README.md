# kmatch

Fast, RapidFuzz-compatible fuzzy string matching for Kotlin Multiplatform.

- **Verified parity** — bit-exact with [RapidFuzz](https://github.com/rapidfuzz/RapidFuzz), checked against 1,460 golden vectors on every commit. Tune thresholds in a Python notebook, ship the numbers to Kotlin unchanged.
- **Correct on full Unicode** — all algorithms operate on code points, never UTF-16 chars. A single emoji is one symbol, astral-plane characters sort correctly, and `defaultProcess` uses the exact Unicode tables of RapidFuzz's C++ backend.
- **Every KMP target** — JVM (serves Android), JS, Wasm, iOS (including `iosX64`), macOS, watchOS, tvOS, Linux, Windows, Android Native.
- **Zero runtime dependencies**, MIT licensed.

> Status: pre-release (`0.1.0-SNAPSHOT`). The parity core is complete and verified; first Maven Central release is upcoming. The bit-parallel performance core lands in 0.2.0 behind the same frozen API.

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

## How parity is verified

`tools/generate_vectors.py` pins RapidFuzz 3.14.5 and emits `GoldenVectors.kt` — 1,460 (inputs, scorer, expected score) cases covering plain ASCII, accented Latin, astral-plane characters, empty strings, strings past 64 code points, token edge cases, and non-Latin scripts through `defaultProcess`. CI asserts **exact** float64 equality on every tested target and regenerates the vectors to catch drift.

`defaultProcess` and tokenization use Unicode tables probed directly from RapidFuzz's C++ backend (`tools/generate_unicode_tables.py`) rather than any host platform's Unicode APIs — the backends genuinely differ from both Python's `re` module and Java's `Character` (underscore handling, NBSP tokenization, simple vs. full case mapping), and the probe-derived tables make kmatch match what RapidFuzz users actually observe.

## Roadmap

- **0.1.0** — Parity core (code-point DP), string extraction, golden vectors on all targets, first Maven Central release.
- **0.2.0** — Myers/Hyyrö bit-parallel edit distance and cutoff short-circuiting behind the unchanged API; published benchmarks vs existing Kotlin libraries.
- **0.3.0** — Generic extraction over `T` with `keySelector`, `dedupe`, `matchingRanges`.
- **1.0.0** — API freeze, documentation site, Wasm demo.

## License

[MIT](LICENSE)
