# 06 — From `git push` to your phone

The last piece: how code in this repo becomes a library running inside
someone's app. Follow one change on its whole journey.

## Act 1 — You push a change

Say you edit `Fuzz.kt` and push to `main`. GitHub notices and starts the
**CI workflow** (`.github/workflows/ci.yml`) on rented cloud machines:

```
your push
   ├── Ubuntu machine:  tests on JVM, JS, Wasm, Linux native
   ├── macOS machine:   compile EVERY target (iOS, watchOS, tvOS, Windows,
   │                    Android Native...), run tests on macOS + an actual
   │                    iOS simulator
   └── Ubuntu machine:  the drift check — reruns tools/generate_vectors.py
                        against real RapidFuzz and fails if your change made
                        any of the 3,260 answers differ
```

Green checkmarks = your change provably didn't break the promise. Red =
GitHub emails you and the change doesn't ship anywhere.

A second workflow (`docs.yml`) rebuilds the playground website on the same
push, so https://likhithsj.github.io/kmatch/ always runs the latest `main`.

## Act 2 — Cutting a release

Releases are deliberately boring (that's a feature). The whole procedure:

```
1. gradle.properties:  version=0.4.0-SNAPSHOT  →  version=0.4.0   (commit)
2. Create a GitHub Release with tag v0.4.0
3. ...that's it. The tag triggers release.yml.
```

The release workflow, on a macOS machine:

1. Runs the full test matrix AGAIN (tests gate publishing — a red test
   means nothing uploads)
2. Compiles the library for all ~20 targets
3. **Signs** every file with the project's GPG key (stored as a GitHub
   secret) — consumers' tools can verify nothing was tampered with
4. Uploads everything to **Maven Central** — the world's package warehouse
   for JVM/Kotlin libraries — where it's checked (valid signature? complete
   metadata?) and then released

Two properties worth knowing:
- **Immutable:** once 0.4.0 is on Central, it can never be changed or
  deleted. A mistake means shipping 0.4.1, never editing 0.4.0. (This
  happened for real: 0.3.0 shipped requiring Java 21 by accident; the fix
  is 0.3.1, and 0.3.0 will say Java-21-only forever.)
- **~30 minutes of propagation:** after the workflow goes green, Central's
  worldwide mirrors take a little while before downloads succeed.

## Act 3 — Someone uses it

A developer anywhere writes one line:

```kotlin
dependencies {
    implementation("io.github.likhithsj:kmatch:0.3.1")
}
```

When they build, Gradle:

1. Downloads a small metadata file for `io.github.likhithsj:kmatch` from
   Maven Central
2. Reads it and **picks the right variant for each of their targets
   automatically** — their Android app gets the JVM jar, their iOS app gets
   the iOS klib, their website gets the JS build. One line, every platform.
3. Caches it locally and links it into their app like any other code.

From then on, `extractOne("latte", menu)` in their codebase executes the
exact bytes Act 2 published.

## Act 4 — Trust, continuously

Three ongoing mechanisms keep the story honest after shipping:

- **The drift check** runs on every future push — the 3,260-answer contract
  with RapidFuzz can't erode quietly.
- **The consumer matrix** (`verify-consumers.yml`) periodically installs the
  *published* artifact exactly like a stranger would and re-runs golden
  assertions on 14 environments — Java 8 through 21, Android, iOS/watchOS/
  tvOS simulators, Windows, Swift-via-SKIE. Results are summarized with
  links in `VERIFICATION.md`.
- **Anyone can re-verify**: the benchmark harness, the vector generator,
  and all the workflows are in the repo. No claim in the README relies on
  "trust me".

## The whole pipeline on one screen

```
 you ──push──▶ GitHub ──▶ ci.yml (test everything + drift check)
                   │
                   ├──▶ docs.yml ──▶ playground website updates
                   │
              tag v0.4.0
                   │
                   ▼
             release.yml (test → sign → upload)
                   │
                   ▼
            Maven Central (immutable, worldwide)
                   │
     implementation("io.github.likhithsj:kmatch:…")
                   │
                   ▼
        someone's app, on any platform 📱💻🌐⌚
```

That's the end of the tour. If you read all six files: you now know where
execution starts, the path every call takes, the math, the speed trick, what
every file is for, and how the whole thing ships. The next best step is
doing: open `samples/menu-search/`, run it, change a query, break a test on
purpose, and watch the machinery react.
