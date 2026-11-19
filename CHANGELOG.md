# Changelog

All releases are on [Maven Central](https://central.sonatype.com/artifact/io.github.likhithsj/kmatch)
as `io.github.likhithsj:kmatch:<version>`. Scoring is a parity contract with
RapidFuzz 3.14.5: a score changes between versions only to close a gap with
RapidFuzz, and every such change is listed here.

## 1.0.1 — 2026-11-19

Drop-in replacement for 1.0.0. No API change. Two parity fixes, both found
by the new differential fuzzer (`tools/differential-fuzz`), which compares
random inputs with RapidFuzz bit for bit.

### Fixed

- **`scoreCutoff` on or next to the true score could return 0.0.**
  `partialRatio`, `partialTokenSortRatio`, `partialTokenSetRatio`,
  `partialTokenRatio`, `weightedRatio`, and extraction using those scorers,
  could return 0.0 when the cutoff was within one floating-point step of the
  true score, the true score itself included.
  `Fuzz.partialRatio("abbabbaabba", "bababa")` is `83.33333333333334`;
  passing that value as `scoreCutoff` returned `0.0` and now returns the
  score, as RapidFuzz does. The practical trigger is feeding a result back in
  as the cutoff ("everything at least as good as my best match"). Cause: the
  implementation followed RapidFuzz's pure-Python fallback, which compares on
  one scale; the C++ backend, which is what RapidFuzz users run, compares
  full-length windows on the 0-100 scale and prefix/suffix windows on the 0-1
  scale.
- **NBSP and NEL did not separate tokens in non-Latin-1 strings.** RapidFuzz's
  C++ tokenizer splits on U+00A0 and U+0085 only in strings that contain a
  code point above U+00FF. kmatch never split on them.
  `tokenSortRatio("Łódź\u00A0Kaliska", "Kaliska Łódź")` was `58.3` and is now
  `100.0`. Affects `tokenSortRatio`, `tokenSetRatio`, `tokenRatio`, the three
  `partialToken*` scorers and `weightedRatio`, on input that has not gone
  through `defaultProcess` or `searchFold` (both already turn these characters
  into plain spaces).

### Who is affected

Scores from 1.0.0 are unchanged unless a call hits one of the two cases above.
Without a `scoreCutoff`, or with a threshold that does not land exactly on an
item's score, the first fix changes nothing. With `defaultProcess` or
`searchFold` as the processor, the second fix changes nothing.

### Verification

- Golden vectors: 3,260 to 4,380. The new ones cover width-dependent
  separators and cutoffs placed exactly on a score and one step either side;
  160 of them fail on 1.0.0.
- Differential fuzz: 0 mismatches in 1,012,086 comparisons (two seeds).
  1.0.0 shows 4,272 mismatches in the first 254,043.

### Also in this release (repository only, not in the artifact)

- `samples/starbucks-menu`: a Compose Multiplatform app for Android and iOS
  that searches a real menu pulled from the web.
- `tools/differential-fuzz`: the fuzzer itself.
- README, VERIFICATION.md, RELEASING.md and the learn guides brought in line
  with the code; `ReadmeSnippetsTest` now compiles and asserts every README
  snippet.

## 1.0.0 — 2026-10-24

First stable release: the API surface is frozen.

- **Accent-insensitive search**: `searchFold` and `stripAccents`.
  `"caffe latte"` matches `"Caffè Latte"` at 100. Conservative fold (Indic
  marks and Hangul untouched), opt-in, outside RapidFuzz parity.
- **Multi-field extraction**: `keySelectors` overloads of `extractOne`,
  `extractTop`, `extractSorted`, `extractAll`. Each field is scored on its
  own and the best field wins.
- VERIFICATION.md gains a recipe for re-checking the parity claim.
- New sample: `samples/migrate-fuzzywuzzy`.

## 0.3.1 — 2026-08-26

- The JVM artifact targets Java 8 bytecode. 0.3.0 required Java 21 at runtime
  by accident. No API or behavior change.

## 0.3.0 — 2026-08-25

First public release.

- All ten `fuzz` scorers, bit-exact with RapidFuzz 3.14.5.
- Hyyrö bit-parallel indel distance; query masks reused across a scan.
- Code-point based throughout; RapidFuzz's exact `default_process` tables.
- `extractOne` / `extractTop` / `extractSorted` / `extractAll`, generic over
  your own types; `dedupe`; `matchingBlocks` / `matchingRanges`.
- JVM, JS, Wasm, iOS, macOS, watchOS, tvOS, Linux, Windows, Android Native.

Versions 0.1.0 and 0.2.0 were development milestones and were never
published.
