package io.github.likhithsj.kmatch

/**
 * A similarity scorer on RapidFuzz's 0-100 scale.
 *
 * Implementations must return a score in `[0, 100]` and, when [scoreCutoff]
 * is non-null and the score would fall below it, return 0.0 instead. All
 * [Fuzz] scorers follow this contract; custom scorers -- including ones from
 * outside this library -- can be supplied as lambdas.
 */
public fun interface Scorer {
    public fun score(s1: String, s2: String, scoreCutoff: Double?): Double
}

/** Ready-to-use [Scorer] instances for every [Fuzz] scorer. */
public object Scorers {
    public val Ratio: Scorer = Scorer { a, b, c -> Fuzz.ratio(a, b, scoreCutoff = c) }
    public val PartialRatio: Scorer = Scorer { a, b, c -> Fuzz.partialRatio(a, b, scoreCutoff = c) }
    public val TokenSortRatio: Scorer = Scorer { a, b, c -> Fuzz.tokenSortRatio(a, b, scoreCutoff = c) }
    public val TokenSetRatio: Scorer = Scorer { a, b, c -> Fuzz.tokenSetRatio(a, b, scoreCutoff = c) }
    public val TokenRatio: Scorer = Scorer { a, b, c -> Fuzz.tokenRatio(a, b, scoreCutoff = c) }
    public val PartialTokenSortRatio: Scorer = Scorer { a, b, c -> Fuzz.partialTokenSortRatio(a, b, scoreCutoff = c) }
    public val PartialTokenSetRatio: Scorer = Scorer { a, b, c -> Fuzz.partialTokenSetRatio(a, b, scoreCutoff = c) }
    public val PartialTokenRatio: Scorer = Scorer { a, b, c -> Fuzz.partialTokenRatio(a, b, scoreCutoff = c) }
    public val WeightedRatio: Scorer = Scorer { a, b, c -> Fuzz.weightedRatio(a, b, scoreCutoff = c) }
    public val QuickRatio: Scorer = Scorer { a, b, c -> Fuzz.quickRatio(a, b, scoreCutoff = c) }
}

/**
 * A single extraction result: the original (unprocessed) [choice], its
 * [score], and its [index] in the input collection.
 */
public data class ExtractedResult(
    val choice: String,
    val score: Double,
    val index: Int,
)

/**
 * Finds the best match for [query] among [choices].
 *
 * When multiple choices tie on the best score, the first one wins (matching
 * RapidFuzz `process.extractOne`). Returns null when no choice reaches
 * [scoreCutoff].
 *
 * @param processor applied to the query and every choice before scoring; the
 *   returned [ExtractedResult.choice] is always the original string.
 * @param scoreCutoff minimum score (inclusive) for a choice to be considered.
 */
public fun extractOne(
    query: String,
    choices: Iterable<String>,
    scorer: Scorer = Scorers.WeightedRatio,
    processor: ((String) -> String)? = null,
    scoreCutoff: Double? = null,
): ExtractedResult? {
    val processedQuery = if (processor != null) processor(query) else query
    val cutoff = scoreCutoff ?: 0.0
    // For built-in scorers, convert the query once and reuse its match masks
    // across the whole scan; custom scorers see the string-based path.
    val prepared = prepareScorer(scorer, processedQuery.toCodePoints())
    var best: ExtractedResult? = null
    var currentCutoff = scoreCutoff
    for ((index, choice) in choices.withIndex()) {
        val processedChoice = if (processor != null) processor(choice) else choice
        val score =
            if (prepared != null) prepared.score(processedChoice.toCodePoints(), currentCutoff ?: 0.0)
            else scorer.score(processedQuery, processedChoice, currentCutoff)
        if (score >= cutoff && (best == null || score > best.score)) {
            best = ExtractedResult(choice, score, index)
            currentCutoff = score
            if (score == 100.0) break
        }
    }
    return best
}

/**
 * Returns the [limit] best matches for [query] among [choices], sorted by
 * score descending; ties keep input order (matching RapidFuzz
 * `process.extract`).
 */
public fun extractTop(
    query: String,
    choices: Iterable<String>,
    limit: Int = 5,
    scorer: Scorer = Scorers.WeightedRatio,
    processor: ((String) -> String)? = null,
    scoreCutoff: Double? = null,
): List<ExtractedResult> {
    require(limit >= 0) { "limit must be >= 0, was $limit" }
    if (limit == 0) return emptyList()
    return extractSorted(query, choices, scorer, processor, scoreCutoff).take(limit)
}

/**
 * Returns all matches reaching [scoreCutoff], sorted by score descending;
 * ties keep input order.
 */
public fun extractSorted(
    query: String,
    choices: Iterable<String>,
    scorer: Scorer = Scorers.WeightedRatio,
    processor: ((String) -> String)? = null,
    scoreCutoff: Double? = null,
): List<ExtractedResult> =
    extractAll(query, choices, scorer, processor, scoreCutoff)
        .sortedByDescending { it.score } // stable: equal scores keep input order

/**
 * Scores every choice and returns all results reaching [scoreCutoff], in
 * input order.
 */
public fun extractAll(
    query: String,
    choices: Iterable<String>,
    scorer: Scorer = Scorers.WeightedRatio,
    processor: ((String) -> String)? = null,
    scoreCutoff: Double? = null,
): List<ExtractedResult> {
    val processedQuery = if (processor != null) processor(query) else query
    val cutoff = scoreCutoff ?: 0.0
    // For built-in scorers, convert the query once and reuse its match masks
    // across the whole scan; custom scorers see the string-based path.
    val prepared = prepareScorer(scorer, processedQuery.toCodePoints())
    val results = ArrayList<ExtractedResult>()
    for ((index, choice) in choices.withIndex()) {
        val processedChoice = if (processor != null) processor(choice) else choice
        val score =
            if (prepared != null) prepared.score(processedChoice.toCodePoints(), scoreCutoff ?: 0.0)
            else scorer.score(processedQuery, processedChoice, scoreCutoff)
        if (score >= cutoff) {
            results.add(ExtractedResult(choice, score, index))
        }
    }
    return results
}
