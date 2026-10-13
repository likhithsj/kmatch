# 07 — Categories and priorities

Real menus and catalogs aren't flat lists — they're trees: *Hot Coffee*,
*Cold Drinks*, *Holiday Drinks*, *Food*… This file explains how category
structure and category *priority* work with kmatch, because the answer
surprises people at first:

> **kmatch has no idea what a category is — on purpose.**

The library scores text. Categories, priorities, seasons, promotions — all
of that is YOUR data and YOUR rules, applied in a couple of lines around
the search call. This file shows those lines.

📍 Working example: the whole pattern lives in
[`samples/menu-search/`](../../samples/menu-search/) —
[the category tree + `allItems()` flatten — MenuSearch.kt:25](https://github.com/likhithsj/kmatch/blob/a3e08a1/samples/menu-search/src/main/kotlin/MenuSearch.kt#L25) ·
[the search call — MenuSearch.kt:46](https://github.com/likhithsj/kmatch/blob/a3e08a1/samples/menu-search/src/main/kotlin/MenuSearch.kt#L46) ·
[keySelector (name + keywords) — MenuSearch.kt:50](https://github.com/likhithsj/kmatch/blob/a3e08a1/samples/menu-search/src/main/kotlin/MenuSearch.kt#L50)

## Step 1 — Searching across categories: flatten once

However many categories you have — 4 or 400 — searching is still ONE call.
Flatten the tree into a single list where each product remembers its
category, and scan that:

```kotlin
enum class Category { HOLIDAY_DRINKS, DRINKS, FOOD, MERCH }
data class Product(val name: String, val keywords: List<String>, val category: Category)

val hits = extractAll(
    query, allProducts,
    keySelector = { "${it.name} ${it.keywords.joinToString(" ")}" },
    processor = ::defaultProcess,
    scoreCutoff = 55.0,
)
```

Why one scan instead of one scan per category? File 04's trick: the query's
masks are built **once** and reused for every product. Four separate scans
would rebuild them four times — and you'd still have to merge the results.

Two variations you get for free:
- **Search inside ONE category:** just pass that subtree's items —
  `extractAll(query, holidayCategory.allItems(), …)`
- **Show results grouped:** `hits.groupBy { it.item.category }`

## Step 2 — Priority is a sort you own

Say *Holiday Drinks* should outrank *regular Drinks*. There are three ways
to say "priority", and they behave differently. Pick the one that matches
what the product actually wants:

### Flavor A — tie-breaker (mildest)

Category only matters when the match quality is EQUAL:

```kotlin
hits.sortedWith(
    compareByDescending<ExtractedItem<Product>> { it.score }
        .thenBy { it.item.category.ordinal }   // enum order = priority order
)
```

A 95-scoring Merch item still beats an 85-scoring Holiday drink. Fairest to
the user; weakest for merchandising.

### Flavor B — soft boost (use this one, usually)

Multiply each score by a per-category weight *close to 1.0*:

```kotlin
fun boostFor(c: Category) = when (c) {
    Category.HOLIDAY_DRINKS -> if (isHolidaySeason()) 1.0 else 0.0  // 0 = hidden off-season
    Category.DRINKS         -> 0.95
    Category.FOOD           -> 0.92
    Category.MERCH          -> 0.88
}

val ranked = hits
    .map { it to it.score * boostFor(it.item.category) }
    .filter { it.second > 0 }
    .sortedByDescending { it.second }
    .map { it.first }
```

### Flavor C — strict tiers (rarely right)

All Holiday results first, then all Drinks, etc., sorted by score within
each tier (`compareBy { category }.thenByDescending { score }`). Danger: a
terrible Holiday match outranks a perfect Merch match. Only do this if the
requirement literally says "category X always on top."

## The holiday example, with real numbers

User types `"mocha"` in December. Text scores come from kmatch; the final
column is yours:

| Product | Category | text score | × boost | final |
|---|---|---|---|---|
| Peppermint Mocha | HOLIDAY | 90 | 1.0 | **90.0** ← 1st |
| Caffè Mocha | DRINKS | 90 | 0.95 | 85.5 ← 2nd |
| Mocha Frappuccino | DRINKS | 86 | 0.95 | 81.7 |
| Mocha Sauce Bottle | MERCH | 84 | 0.88 | 73.9 |

Read what the boost did: both mochas matched the text equally (90) — the
boost broke the tie toward the holiday item. But relevance still rules:
type `"frappuccino"` instead, and Peppermint Mocha's text score is so low
that no boost rescues it. **Priority nudges; it never forces an irrelevant
item on the user.** That's the property that makes Flavor B feel right.

## The knob you're actually tuning

The GAP between weights answers one question: *"how much worse may a
high-priority match be and still win?"* With 1.0 vs 0.95, holiday wins
whenever it's within ~5% on text quality. Widen the gap to promote harder;
shrink it toward Flavor A to promote gentler. Tune by complaint, not by
theory.

## Four guardrails

1. **Keep weights in the 0.9–1.0 band.** A weight like 0.5 lets bad
   matches in the favored category beat excellent ones elsewhere — users
   experience that as "search is broken."
2. **`scoreCutoff` is the QUALITY bar, not a priority tool.** It runs
   before your boost and answers "is this even a plausible match?" Mixing
   the two makes both impossible to tune.
3. **Seasonality is data, not code.** The holiday boost turning on Nov 1
   and off Jan 2 is one `if` on the date (or a server-driven config value)
   — the search call never changes, nothing redeploys.
4. **Later, blend more signals the same way.** Popularity, in-stock,
   promotions: `score × categoryBoost × popularityBoost`. kmatch keeps
   answering only "how well does the text match?" — which is also why its
   answers stay verifiable against RapidFuzz no matter what ranking rules
   you stack on top.

## Why the library refuses to know about categories

It might seem lazy — "why doesn't kmatch just take a `priorities` map?" —
but it's the design that keeps everything else true:

- Your domain model stays yours (tree, tags, graph — anything flattens to
  a list + `keySelector`).
- Business rules change weekly; the frozen, RapidFuzz-verified scoring
  core never has to change with them.
- Every ranking decision is one visible `sortedWith` in YOUR code —
  debuggable with a print statement, A/B-testable without touching the
  matcher.

**Previously:** [06-from-push-to-your-phone.md](06-from-push-to-your-phone.md) ·
**Index:** [README.md](README.md)
