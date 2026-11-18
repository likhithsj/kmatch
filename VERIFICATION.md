# Verification

Evidence that the published artifact `io.github.likhithsj:kmatch:1.0.0`
works, as a consumer would use it, on every environment CI can execute. The
executed checks in the table run **against the artifact downloaded from Maven
Central** (never this repository's sources) and assert golden values that are
bit-exact with Python RapidFuzz 3.14.5. The one exception is marked: the
browser playground is built from `main`.

**Primary evidence — the consumer verification matrix**
([workflow](.github/workflows/verify-consumers.yml) ·
[green run on 1.0.0](https://github.com/likhithsj/kmatch/actions/runs/40071920189) ·
consumer projects: [`samples/consumer-check`](samples/consumer-check),
[`samples/consumer-check-android`](samples/consumer-check-android)):

| Environment | How it is verified | Result |
|---|---|---|
| JVM, Java 21 runtime | Golden-value tests executed (Ubuntu) | ✅ pass |
| JVM, Java **8** runtime | Same tests executed on a Temurin 8 launcher | ✅ pass |
| JVM, Java **11** runtime | Same tests executed | ✅ pass |
| JVM, Java **17** runtime | Same tests executed | ✅ pass |
| **Android** (AGP 8.7 library module, minSdk 24) | Module builds against kmatch; unit tests executed | ✅ pass |
| JS (Node) | Tests executed | ✅ pass |
| Wasm (Node) | Tests executed | ✅ pass |
| JS (browser) | The [live playground](https://likhithsj.github.io/kmatch/) is the library running in a browser (built from `main`, not from Central) | ✅ live |
| Linux x64 (native) | Test binary executed | ✅ pass |
| **Windows x64** (native, mingw) | Test binary executed on a Windows runner | ✅ pass |
| macOS arm64 (native) | Tests executed | ✅ pass |
| **iOS** (simulator, arm64) | Tests executed in an iOS simulator | ✅ pass |
| **watchOS** (simulator) | Tests executed in a watchOS simulator | ✅ pass |
| **tvOS** (simulator) | Tests executed in a tvOS simulator | ✅ pass |
| **Swift / SKIE** | [SKIE sample](samples/ios-skie): SKIE-built framework, Swift assertions of golden values — [green run on 1.0.0](https://github.com/likhithsj/kmatch/actions/runs/40071920193) | ✅ pass |
| iOS device (`iosArm64`), `iosX64`, `linuxArm64`, `androidNativeArm64` | Consumer **compiles** against the published klib (no CI hardware can execute these) | ✅ compile-verified |

**Library-side guarantees, continuously enforced**
([CI workflow](https://github.com/likhithsj/kmatch/actions/workflows/ci.yml),
every push to `main` and every pull request):

- **4,380 golden vectors** — every scorer × inputs spanning ASCII, accented
  Latin, astral-plane emoji, CJK/Cyrillic/RTL scripts, >64-code-point
  strings, width-dependent token separators, and `scoreCutoff` cases down to
  cutoffs exactly on the score — asserted for exact float64 equality with
  pinned RapidFuzz 3.14.5, regenerated in CI to catch drift.
- **Differential fuzzing** ([tools/differential-fuzz](tools/differential-fuzz/),
  run by hand, not in CI): random inputs through RapidFuzz and kmatch,
  compared bit for bit. The current sources are clean over 1,012,086
  comparisons (seeds 1 and 777).
- **JVM bytecode floor**: every class in the published jar is class-file
  version 52 (Java 8) — verified by decompiling the artifact downloaded from
  Central.
- **Signed release pipeline**: artifacts are GPG-signed and published by the
  [release workflow](https://github.com/likhithsj/kmatch/actions/workflows/release.yml);
  Maven Central releases are immutable.
- **Benchmarks** (harness in-repo, reproducible): ~8× faster `ratio` and
  ~12× faster `extractOne` than me.xdrop/fuzzywuzzy, ~22× faster `ratio`
  than kt-fuzzy. See the README's Performance section.

**Known parity gaps in the published 1.0.0**

The differential fuzzer was written after 1.0.0 shipped, and its first run
found two places where 1.0.0 does not match RapidFuzz. Both are fixed in the
repository and covered by golden vectors; neither is in a release yet.

- `partialRatio`, `partialTokenSortRatio`, `partialTokenSetRatio`,
  `partialTokenRatio` and `weightedRatio` (and extraction using them) can
  return 0.0 when `scoreCutoff` is within one floating-point step of the
  true score, the true score itself included (it depends on how that
  particular score rounds). Without a cutoff, or with a cutoff
  not sitting on the score, results are exact.
- The six token scorers and `weightedRatio`, on input that has **not** gone
  through `defaultProcess` or `searchFold`, do not split tokens on NBSP
  (U+00A0) or NEL (U+0085) when the string contains a character above
  U+00FF; RapidFuzz does.

Reproduce them: `KMATCH_VERSION=1.0.0 tools/differential-fuzz/run.sh`.

**Honest limits**

- Six published targets are not exercised by the consumer project at all:
  `macosX64`, `watchosArm64`, `tvosArm64`, `androidNativeArm32`,
  `androidNativeX86`, `androidNativeX64`. They are built and published by
  the release workflow from the same shared sources.

- Targets marked *compile-verified* have never had their test binaries
  executed (no CI hardware exists for them). The library is 100% shared
  common Kotlin with no per-platform code paths, and the same sources pass
  on 13 executed environments, so residual risk is compiler-level, not
  library-level. This is standard practice for Kotlin Multiplatform
  libraries.
- The multiplatform (non-JVM) artifacts require a reasonably recent Kotlin
  toolchain in the consuming project; the JVM jar itself is plain Java 8
  bytecode consumable from any build tool.
- kmatch is a fuzzy *matcher* (search-as-you-type over catalogs, dedupe,
  "did you mean"), efficient into the tens of thousands of candidates per
  query. It is not an indexed full-text search engine and does not do
  semantic similarity or cross-script transliteration.
- The API surface is frozen as of 1.0.0. Scoring behavior is the RapidFuzz
  parity contract: it changes only to close a gap with RapidFuzz, as the two
  fixes above do.

**Reproduce any of this**

- Re-run the matrix: repo → Actions → *Verify consumers* → *Run workflow*.
- Locally: `cd samples/consumer-check && ../../gradlew jvmTest jsNodeTest`
  (any machine), native/simulator tasks on their host OS.
- The playground needs nothing: <https://likhithsj.github.io/kmatch/>.

## Verify the parity claim yourself (~10 minutes)

The "bit-exact with RapidFuzz" claim is mechanically checkable — don't take
this document's word for it:

```bash
git clone https://github.com/likhithsj/kmatch && cd kmatch
pip install rapidfuzz==3.14.5         # the pinned reference implementation
python3 tools/generate_vectors.py     # re-asks real RapidFuzz all 4,380 questions
python3 tools/generate_unicode_tables.py
git diff --exit-code                  # empty diff = committed expectations ARE RapidFuzz's answers
./gradlew jvmTest                     # kmatch reproduces every one of them, exactly
tools/differential-fuzz/run.sh        # optional: ~254,000 random comparisons, must report 0 mismatches
```

What this proves: the committed `GoldenVectors.kt` was not hand-written —
regenerating it from the real Python RapidFuzz produces an identical file —
and the library passes all of it with exact float64 equality. CI runs the
same drift check on every push ([ci.yml](.github/workflows/ci.yml), job
*Golden vector drift check*), so the claim re-proves itself continuously,
not once. The benchmark numbers are reproducible the same way:
`KMATCH_BENCH=1 ./gradlew jvmTest --tests '*BenchmarkTest' --rerun` (the two
comparison libraries are test-only dependencies of the harness).
