# Migrating from fuzzywuzzy (me.xdrop) to kmatch

A worked, runnable migration: the fuzzywuzzy string round-trip as it exists
in real apps (`LegacyProductSearch`), the kmatch drop-in with the same
signature (`KmatchProductSearch`), and the side-by-side evidence.

```bash
../../gradlew -p . run    # the comparison table
../../gradlew -p . test   # the pinned evidence
```

## What the legacy pattern does wrong

```kotlin
FuzzySearch.extractSorted(query, products.map { it.name }, 60)   // types thrown away
    .map { m -> products.first { it.name == m.string } }         // identity rebuilt by name
```

Object identity is destroyed in step 1 and guessed back in step 2. With two
forms sharing a display name (HOT and ICED "Caffè Latte"), `first { }` maps
both matches to the same form — the other is unreachable. The score and the
index that fuzzywuzzy *did* return are dropped on the floor.

## The replacement

```kotlin
extractSorted(query, products, keySelector = { it.name },
              processor = ::searchFold, scoreCutoff = 60.0)
    .map { it.item }     // the scored object itself; nothing to look up
```

Same call shape, so the swap inside an interactor is mechanical. What changes
beyond the bug fix:

| | fuzzywuzzy 1.4.0 | kmatch |
|---|---|---|
| Result carries your object | via `BoundExtractedResult.getReferent()` (typed overload, often unused) | always: `ExtractedItem<T>.item` |
| Score parity | its own port of old fuzzywuzzy behavior | bit-exact with RapidFuzz, 3,260 golden vectors per commit |
| Unicode | UTF-16 chars (emoji counts as 2) | code points |
| Text cleanup | fixed | `processor` hook → accent folding ("jalapeno" finds "Jalapeño") |
| Cutoff | filter after scoring | pushed into the scorer (early exits, mask reuse per scan) |
| Platforms | JVM only | all Kotlin Multiplatform targets (same behavior on iOS) |
| Score type | `Int` | `Double` 0–100 |

## Migration checklist

1. **Fix identity first (optional but safe):** move to fuzzywuzzy's own typed
   overload (`extractSorted(query, products, ToStringFunction { it.name }, cutoff)`
   → `.referent`). Zero-risk refactor, and it lands the code on exactly the
   kmatch call shape.
2. **Swap the engine:** change the dependency, replace the call as above,
   `referent` → `item`.
3. **Re-check cutoffs and pinned scores.** kmatch matches RapidFuzz, not
   fuzzywuzzy, and returns `Double` — any test asserting an exact legacy
   score, and any cutoff tuned by eye against legacy scores, needs one review
   pass. This is expected migration work, not a regression.
4. **Run the side-by-side on the real catalog** before flipping: point
   `MigrationDemo`-style code at production data and real query logs, diff
   the top-5 lists, and review only the rows that differ. Expect differences
   to cluster where legacy was wrong (duplicates, accents, Unicode).
5. **Then build what legacy couldn't:** keyword aliases via `keySelector`
   (`"${name} ${keywords}"`), category boosts on the surviving score
   (see `samples/menu-search`), match highlighting via `matchingRanges`.

## What the tests pin

- `legacyCanNeverReturnTheSecondSameNamedForm` — the bug, demonstrated
- `kmatchReturnsEachFormAsItself` — the fix
- `unaccentedQueryFindsAccentedProduct` / `accentedAndUnaccentedQueriesScoreTheSame` — the processor win
- `sameSignatureSameHappyPath` — everyday queries don't change
- `scoresSurviveForRanking` — the ranking hook stays open
