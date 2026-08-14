# Module kmatch

Fast, RapidFuzz-compatible fuzzy string matching for Kotlin Multiplatform.

Every scorer in [io.github.likhithsj.kmatch.Fuzz] returns exactly what the
corresponding `rapidfuzz.fuzz` function returns (0–100 scale, `Double`),
verified against golden vectors generated from a pinned RapidFuzz version.
Extraction ([io.github.likhithsj.kmatch.extractOne] and friends) scans
collections with bit-parallel edit distance and per-query mask reuse.

# Package io.github.likhithsj.kmatch

Scorers, extraction, dedupe, and match highlighting. Start with
[Fuzz] for pairwise scores, [extractOne] for collection search,
[dedupe] for collapsing near-duplicates, and [matchingRanges] for
highlighting why a choice matched.
