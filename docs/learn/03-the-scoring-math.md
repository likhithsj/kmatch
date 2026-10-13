# 03 — The scoring math

Every scorer in `Fuzz.kt` answers "how similar?" differently. This file
shows each one with arithmetic you can check by hand (or paste into the
playground — every number here is the real output).

## The base: `ratio`

📍 [public wrapper — Fuzz.kt:29](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Fuzz.kt#L29) · [ratioCps — Fuzz.kt:172](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Fuzz.kt#L172) · [the formula — Indel.kt:179](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Indel.kt#L179)

The formula from file 01, applied to whole strings:

```
score = 100 × (1 − edits ÷ (lenA + lenB))
```

where **edits counts insertions and deletions only** and comes from the
longest common backbone (LCS):

```
edits = lenA + lenB − 2 × LCS
```

Worked example — `ratio("this is a test", "this is a test!")`:

```
lengths: 14 and 15.  Backbone: all 14 characters of the first string.
edits = 14 + 15 − 28 = 1
score = 100 × (1 − 1/29) = 96.55172413793103
```

### ⚠️ The most common misunderstanding

This is **not** "normalized Levenshtein". Levenshtein also allows
*replacing* a character as one edit; indel counts a replace as TWO edits
(delete + insert). Different formula, different numbers:

```
ratio("myself", "me")  =  50.0   under indel      (what kmatch/RapidFuzz do)
                       ≈  33.3   under Levenshtein (what people often expect)
```

If someone's hand-calculation disagrees with the library, this is almost
always why.

## `partialRatio` — finding a needle in a haystack

📍 [public wrapper — Fuzz.kt:43](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Fuzz.kt#L43) · [partialRatioCps — Fuzz.kt:238](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Fuzz.kt#L238) · [the sliding windows — Fuzz.kt:187](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Fuzz.kt#L187)

Question it answers: *"does the SHORT string appear, roughly, somewhere
INSIDE the long one?"*

How: slide a window the size of the short string across the long string,
compute `ratio` for every window, keep the best.

```
partialRatio("this is a test", "this is a test!") = 100
```

because the window covering the first 14 characters of the long string
matches perfectly — the trailing `!` never hurts. Use this when queries are
short and the things being searched are long (titles, descriptions).

## `tokenSortRatio` — word order doesn't matter

📍 [public wrapper — Fuzz.kt:54](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Fuzz.kt#L54) · [tokenSortRatioCps — Fuzz.kt:344](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Fuzz.kt#L344)

Split into words, sort the words alphabetically, join, then plain `ratio`:

```
"fuzzy wuzzy was a bear"  → sort → "a bear fuzzy was wuzzy"
"wuzzy fuzzy was a bear"  → sort → "a bear fuzzy was wuzzy"
                                    identical → 100
```

## `tokenSetRatio` — extra words don't matter (much)

📍 [public wrapper — Fuzz.kt:69](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Fuzz.kt#L69) · [tokenSetRatioCps — Fuzz.kt:347](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Fuzz.kt#L347)

Splits both into word SETS, looks at the overlap, and is generous when one
side's words are a subset of the other's:

```
tokenSetRatio("fuzzy was a bear", "fuzzy was a bear but not a dog") = 100
```

All of the first string's words appear in the second → perfect score, no
matter how much extra the second one says. Great for matching a short query
against verbose titles.

## `tokenRatio` / `partialToken...` — combinations

`tokenRatio` = best of tokenSort and tokenSet. The `partialToken...`
variants do the same word tricks but score windows instead of whole strings.
Nothing new conceptually — just the two ideas composed.

## `weightedRatio` (WRatio) — the "just pick for me" scorer

📍 [public wrapper — Fuzz.kt:131](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Fuzz.kt#L131) · [the decision tree — Fuzz.kt:445](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Fuzz.kt#L445) · [the sacred constants — Fuzz.kt:450](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Fuzz.kt#L450)

The default everywhere. It's a small decision tree, with constants copied
exactly from RapidFuzz (never round these — parity!):

```
lenRatio = longer length ÷ shorter length

if lenRatio < 1.5:    best of  ratio  and  tokenRatio × 0.95
if 1.5 ≤ lenRatio ≤ 8: also try partialRatio × 0.9, partial tokens × 0.855
if lenRatio > 8:       partial comparisons get scale 0.6 instead of 0.9
```

The scaling (×0.95, ×0.9…) means "a partial/word-order match is good, but a
little less convincing than an exact one." Example:

```
weightedRatio("hello world", "hello world xy")
  plain ratio ≈ 88        token-set: "hello world" ⊂ other → 100 × 0.95
  → 95.0
```

## `quickRatio` (QRatio)

📍 [Fuzz.kt:145](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Fuzz.kt#L145)

`ratio` with one extra rule: either string empty (after processing) → 0.

## `scoreCutoff` — the contract on every scorer

📍 [the skip-the-work check — Indel.kt:187](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Indel.kt#L187)

Passing `scoreCutoff = 90` means: *"if the answer would be below 90, give me
0.0 instead."* Two things make it more than a filter:

1. **It speeds things up.** The engine checks length bounds first and skips
   the whole comparison when the cutoff is mathematically unreachable
   (file 02, stop 5).
2. **It's honored everywhere, even internally.** WRatio chains cutoffs into
   its sub-scorers (dividing by those scale factors — which can push an
   internal cutoff above 100, meaning "impossible, return 0"). An early
   version had a bug where shortcut paths ignored this; it was caught by
   review, fixed, and locked in with 1,800 dedicated cutoff test vectors.

## Processing happens BEFORE all of this

📍 [defaultProcess — Process.kt:16](https://github.com/likhithsj/kmatch/blob/f08b4be/src/commonMain/kotlin/io/github/likhithsj/kmatch/Process.kt#L16)

Every scorer takes an optional `processor`. If you pass `::defaultProcess`,
both strings are lowercased/de-punctuated *first*, and only then scored.
That's why `ratio("Test String!", "test string", processor = ::defaultProcess)`
is 100: after processing, the strings are literally identical. The scorers
themselves never make case/punctuation decisions — that's the processor's
job, and you can supply your own (the playground's "fold diacritics" option
is exactly that: a custom processor that strips accents first).

**Next:** [04-the-bit-parallel-trick.md](04-the-bit-parallel-trick.md) —
how the backbone-counting is made absurdly fast.
