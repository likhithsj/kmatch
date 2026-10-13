# 01 — Start here

## What is kmatch, in one sentence?

kmatch answers the question: **"how similar are these two pieces of text?"**
— with a number from 0 (nothing alike) to 100 (identical).

That one ability turns out to power a lot of features:

- A search box that survives typos ("carmel machiato" finds *Caramel Macchiato*)
- "Did you mean…?" suggestions
- Finding duplicate records ("Jon Smith" vs "John Smith")
- Matching what a user typed against a catalog, menu, or contact list

## The one mental model

Everything in this library is built on a single idea:

> **Count the cheapest way to edit one string into the other.
> Fewer edits needed = more similar = higher score.**

Example: turn `"cat"` into `"cart"` — you insert one letter, `r`. One edit.
Turn `"cat"` into `"dog"` — you basically have to replace everything. Many
edits. So cat/cart scores high, cat/dog scores ~0.

The score formula is just that idea dressed up:

```
score = 100 × (1 − edits ÷ (length A + length B))
```

Every fancy feature — word-order handling, searching collections, dedupe —
is a layer on top of this one counting trick. File 03 shows the math in
detail; file 04 shows the trick that makes the counting fast.

## "Where does the code start?"

Here's the thing that trips people up: **a library has no `main()`**. Nothing
in `src/` runs on its own. The code "starts" when *someone else calls it* —
the entry point is whichever public function a user calls first.

There are three places where execution genuinely begins, depending on who's
running:

**1. A real app (the normal case).** An app adds one line to its build:

```kotlin
implementation("io.github.likhithsj:kmatch:0.3.1")
```

…and then calls a function. That call is the start:

```kotlin
val best = extractOne("latte", menuItems)   // ← execution enters the library HERE
```

File 02 follows exactly this call through every file it touches.

**2. The demo app** (`demo/src/jsMain/kotlin/Main.kt`). This one DOES have a
`fun main()` — it's the playground website. Its `main()` wires browser input
boxes to the same public functions an app would call. Good place to see the
library used for real.

**3. The tests** (`src/commonTest/`). Every `@Test` function is a tiny
program that calls the library and checks the answer. If you want to run the
code right now, this is the fastest start:

```sh
./gradlew jvmTest
```

## The three layers of the library

Think of the source as a three-floor building. Calls come in at the top and
travel downward:

```
┌───────────────────────────────────────────────────────────┐
│ FLOOR 3: "search my stuff"                                │
│   extractOne / extractTop / dedupe / matchingRanges       │
│   (Extract.kt, Dedupe.kt, Align.kt)                       │
├───────────────────────────────────────────────────────────┤
│ FLOOR 2: "score two strings"                              │
│   Fuzz.ratio, tokenSortRatio, weightedRatio, ...          │
│   (Fuzz.kt, Process.kt)                                   │
├───────────────────────────────────────────────────────────┤
│ FLOOR 1: "count the edits"  ← the engine                  │
│   indel distance via bit-parallel LCS                     │
│   (Indel.kt, CodePoints.kt)                               │
└───────────────────────────────────────────────────────────┘
```

- You call floor 3 when you have a *collection* to search.
- You call floor 2 when you have exactly *two strings*.
- Nobody outside the library calls floor 1 directly — floors 2 and 3 use it.

## One promise shapes the whole design

kmatch guarantees its scores are **bit-for-bit identical** to a famous Python
library called RapidFuzz. Why care? Because data scientists tune thresholds
("only show matches above 87") in Python notebooks — and with identical
scores, those numbers can be shipped into a Kotlin app unchanged.

This promise explains things you'll see everywhere in the code:

- Oddly specific constants (`0.95`, `1.5`, `8.0` in `weightedRatio`) —
  copied exactly from RapidFuzz, never "improved"
- A giant generated test file (`GoldenVectors.kt`) — 3,260 answers recorded
  from the real RapidFuzz, re-checked on every change
- Comments saying "mirrors RapidFuzz" — meaning: this code is deliberately
  shaped like the original, so the outputs can't drift

**Next:** [02-journey-of-a-call.md](02-journey-of-a-call.md) — follow one
real call through the whole building.
