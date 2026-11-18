# Module kmatch

Fast, RapidFuzz-compatible fuzzy string matching for Kotlin Multiplatform.

Every scorer in [io.github.likhithsj.kmatch.Fuzz] returns exactly what the
corresponding `rapidfuzz.fuzz` function returns (0–100 scale, `Double`),
verified against golden vectors generated from a pinned RapidFuzz version.
Extraction ([io.github.likhithsj.kmatch.extractOne] and friends) scans
collections with bit-parallel edit distance, preparing the query once per
scan.

# Package io.github.likhithsj.kmatch

Scorers, extraction, dedupe, match highlighting, and accent folding. Start
with [Fuzz] for pairwise scores, [extractOne] for collection search (its
`keySelectors` overloads score several fields per item and keep the best),
[dedupe] for collapsing near-duplicates, [matchingRanges] for highlighting
why a choice matched, and [searchFold] as the processor for
accent-insensitive search.
