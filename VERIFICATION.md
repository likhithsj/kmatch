# Verification

Evidence that the published artifact `io.github.likhithsj:kmatch:0.3.1`
works, as a consumer would use it, on every supported environment. All
executed checks below run **against the artifact downloaded from Maven
Central** (never this repository's sources) and assert golden values that are
bit-exact with Python RapidFuzz 3.14.5.

**Primary evidence — the consumer verification matrix**
([workflow](.github/workflows/verify-consumers.yml) ·
[green run](https://github.com/likhithsj/kmatch/actions/runs/34785412696) ·
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
| JS (browser) | The [live playground](https://likhithsj.github.io/kmatch/) is the library running in a browser | ✅ live |
| Linux x64 (native) | Test binary executed | ✅ pass |
| **Windows x64** (native, mingw) | Test binary executed on a Windows runner | ✅ pass |
| macOS arm64 (native) | Tests executed | ✅ pass |
| **iOS** (simulator, arm64) | Tests executed in an iOS simulator | ✅ pass |
| **watchOS** (simulator) | Tests executed in a watchOS simulator | ✅ pass |
| **tvOS** (simulator) | Tests executed in a tvOS simulator | ✅ pass |
| **Swift / SKIE** | [SKIE sample](samples/ios-skie): SKIE-built framework, Swift assertions of golden values — [green run](https://github.com/likhithsj/kmatch/actions/runs/33223473748) | ✅ pass |
| iOS device (`iosArm64`), `iosX64`, `linuxArm64`, Android Native | Consumer **compiles and links** against the published klib (no CI hardware can execute these) | ✅ compile-verified |

**Library-side guarantees, continuously enforced**
([CI workflow](https://github.com/likhithsj/kmatch/actions/workflows/ci.yml),
every commit):

- **3,260 golden vectors** — every scorer × inputs spanning ASCII, accented
  Latin, astral-plane emoji, CJK/Cyrillic/RTL scripts, >64-code-point
  strings, and `scoreCutoff` edge cases — asserted for exact float64 equality
  with pinned RapidFuzz 3.14.5, regenerated in CI to catch drift.
- **JVM bytecode floor**: every class in the published jar is class-file
  version 52 (Java 8) — verified by decompiling the artifact downloaded from
  Central.
- **Signed release pipeline**: artifacts are GPG-signed and published by the
  [release workflow](https://github.com/likhithsj/kmatch/actions/workflows/release.yml);
  Maven Central releases are immutable.
- **Benchmarks** (harness in-repo, reproducible): ~8× faster `ratio` and
  ~12× faster `extractOne` than me.xdrop/fuzzywuzzy, ~22× faster `ratio`
  than kt-fuzzy. See the README's Performance section.

**Honest limits**

- Targets marked *compile-verified* have never had their test binaries
  executed (no CI hardware exists for them). The library is 100% shared
  common Kotlin with no per-platform code paths, and the same sources pass
  on ten executed environments, so residual risk is compiler-level, not
  library-level. This is standard practice for Kotlin Multiplatform
  libraries.
- The multiplatform (non-JVM) artifacts require a reasonably recent Kotlin
  toolchain in the consuming project; the JVM jar itself is plain Java 8
  bytecode consumable from any build tool.
- kmatch is a fuzzy *matcher* (search-as-you-type over catalogs, dedupe,
  "did you mean"), efficient into the tens of thousands of candidates per
  query. It is not an indexed full-text search engine and does not do
  semantic similarity or cross-script transliteration.
- API is pre-1.0: scoring behavior is frozen (the RapidFuzz parity
  contract), the API surface freezes at 1.0 after early-adopter feedback.

**Reproduce any of this**

- Re-run the matrix: repo → Actions → *Verify consumers* → *Run workflow*.
- Locally: `cd samples/consumer-check && ../../gradlew jvmTest jsNodeTest`
  (any machine), native/simulator tasks on their host OS.
- The playground needs nothing: <https://likhithsj.github.io/kmatch/>.
