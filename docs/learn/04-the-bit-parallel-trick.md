# 04 — The bit-parallel trick (why it's fast)

Everything in file 03 reduced to one expensive step: **find the longest
common backbone (LCS) of two character arrays**. This file explains how
`Indel.kt` makes that step very fast. This is the cleverest code in the
repo, and the only part that needs a bit of a "whoa" moment — take it
slowly.

## The slow, obvious way

The textbook method builds a grid: one row per character of string A, one
column per character of string B, and fills every cell with "best backbone
length so far". Two strings of 60 characters = 3,600 little cells, each a
comparison plus bookkeeping. It works (version 0.1.0 of this library shipped
exactly that — correct first, fast later), but it touches every cell one at
a time.

## The idea: 64 lightbulbs inside one number

A `Long` in Kotlin is a 64-bit number. Don't think of it as a number —
think of it as a **row of 64 lightbulbs** (bits), each one ON (1) or OFF (0).

Here's the magic: the CPU can update **all 64 bulbs in a single
instruction**. One `and`, one `+`, one `or` — each touches all 64 bits at
once. So if we can encode "one grid row of the textbook method" into the
bulbs of one Long, we can process a whole row in ~5 instructions instead of
64 cell updates. That's the entire trick, known as Hyyrö's algorithm. The
inner loop in `Indel.kt` is literally this:

```kotlin
val u = s and m          // which bulbs match this character?
s = (s + u) or (s - u)   // update all 64 positions at once
```

Two lines. All the cleverness of the 3,600-cell grid, compressed.

## How does text become bulbs? Masks.

Step one is building a **mask** for the pattern (say the query,
`"banana"`): for each distinct character, one Long whose ON bulbs mark where
that character sits in the pattern:

```
pattern:   b a n a n a        (positions 0..5)
mask['b'] = 000001            (bulb 0 on)
mask['a'] = 101010            (bulbs 1, 3, 5 on)
mask['n'] = 010100            (bulbs 2, 4 on)
```

Then we walk the OTHER string one character at a time. For each of its
characters we grab that character's mask and run the two-line update. The
state `s` starts as "all bulbs ON", and bulbs get *switched off* as parts of
the backbone are found. At the end:

```
backbone length (LCS) = number of OFF bulbs
```

Counting off-bulbs is one CPU instruction too (`countOneBits` on the
inverse). You don't need to *derive* why `(s + u) or (s − u)` does the right
thing (that's a published paper's job) — the repo instead **proves** it:
`BitParallelTest` generates thousands of random string pairs and checks the
bulb method gives the exact same answer as the slow textbook grid, every
time, including right at the tricky 63/64/65-character boundary.

## What if the pattern is longer than 64 characters?

Use several Longs side by side — bulbs 0–63 in the first, 64–127 in the
second, and so on ("the blocked variant"). The only subtlety is that the `+`
in the update can overflow one Long and must *carry* into the next, like
column addition in primary school. That's the `carry` juggling you'll see
in `IndelPattern.lcsBlocked`.

In `Indel.kt` you'll find both paths: a single-Long fast path for patterns
up to 64 characters (most queries), and the blocked path above that.

## The second trick: build once, reuse 20,000 times

Look at the masks again — they depend **only on the pattern** (the query),
not on the text being scanned. So when `extractOne` searches a collection:

```
build masks for "latte"          ← once
score menu item 1 with them      ← cheap
score menu item 2 with them      ← cheap
... 20,000 times
```

That's what `prepareScorer` (`CachedScorer.kt`) sets up, and why calling
`extract*` beats writing your own loop over `Fuzz.ratio` — your loop would
rebuild the masks for every single item.

## The third trick: don't compare at all

Before any bulbs light up, `indelNormalizedSimilarity` checks the length
gap. A 5-character query can never score above a certain bound against a
50-character string — if that bound is already below your `scoreCutoff`,
the comparison returns 0.0 having done *no character work at all*. In a big
scan with a decent cutoff, a huge fraction of candidates are dismissed this
way.

## What all three tricks add up to

Measured by the benchmark in this repo (`BenchmarkTest`, run it yourself
with `KMATCH_BENCH=1 ./gradlew jvmTest --tests '*BenchmarkTest'`):

```
search 20,000 items, old textbook grid:   ~66 ms
search 20,000 items, bulbs + reuse:       ~4.6 ms
...and with a cutoff of 80:               ~4.0 ms
```

Same answers (the golden vectors guarantee it), ~14× less waiting.

**Next:** [05-map-of-every-file.md](05-map-of-every-file.md) — now that you
know the ideas, a tour of where each one lives.
