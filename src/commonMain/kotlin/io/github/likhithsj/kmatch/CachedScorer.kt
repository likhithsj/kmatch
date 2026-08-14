package io.github.likhithsj.kmatch

/*
 * Per-query preparation for the built-in scorers, used by the extraction
 * functions: the query is converted to code points once, and for scorers
 * whose hot path is a single indel comparison the query-side match masks are
 * built once and reused across the entire collection scan.
 *
 * Scores are unchanged -- this is purely a caching layer over the same
 * code-point implementations the public Fuzz wrappers call.
 */

/** A scorer specialized to one prepared query; not thread-safe. */
internal fun interface PreparedScorer {
    /** [scoreCutoff] on the 0-100 scale; 0.0 deactivates it. */
    fun score(choice: IntArray, scoreCutoff: Double): Double
}

/**
 * Returns a [PreparedScorer] for [query] when [scorer] is one of the built-in
 * [Scorers] instances, or null for custom scorers (which only see strings).
 */
internal fun prepareScorer(scorer: Scorer, query: IntArray): PreparedScorer? = when {
    scorer === Scorers.Ratio -> {
        val pattern = IndelPattern(query)
        PreparedScorer { c, cutoff ->
            indelNormalizedSimilarity(pattern, c, 0, c.size, cutoff / 100.0) * 100.0
        }
    }
    scorer === Scorers.QuickRatio -> {
        val pattern = IndelPattern(query)
        PreparedScorer { c, cutoff ->
            if (query.isEmpty() || c.isEmpty()) 0.0
            else indelNormalizedSimilarity(pattern, c, 0, c.size, cutoff / 100.0) * 100.0
        }
    }
    scorer === Scorers.TokenSortRatio -> {
        val qSorted = sortJoin(splitTokens(query))
        val pattern = IndelPattern(qSorted)
        PreparedScorer { c, cutoff ->
            val cSorted = sortJoin(splitTokens(c))
            indelNormalizedSimilarity(pattern, cSorted, 0, cSorted.size, cutoff / 100.0) * 100.0
        }
    }
    scorer === Scorers.PartialRatio -> {
        val pattern = IndelPattern(query)
        PreparedScorer { c, cutoff -> partialRatioCpsCached(pattern, query, c, cutoff) }
    }
    scorer === Scorers.PartialTokenSortRatio -> {
        val qSorted = sortJoin(splitTokens(query))
        val pattern = IndelPattern(qSorted)
        PreparedScorer { c, cutoff ->
            partialRatioCpsCached(pattern, qSorted, sortJoin(splitTokens(c)), cutoff)
        }
    }
    scorer === Scorers.TokenSetRatio -> PreparedScorer { c, cutoff -> tokenSetRatioCps(query, c, cutoff) }
    scorer === Scorers.TokenRatio -> PreparedScorer { c, cutoff -> tokenRatioCps(query, c, cutoff) }
    scorer === Scorers.PartialTokenSetRatio -> PreparedScorer { c, cutoff -> partialTokenSetRatioCps(query, c, cutoff) }
    scorer === Scorers.PartialTokenRatio -> PreparedScorer { c, cutoff -> partialTokenRatioCps(query, c, cutoff) }
    scorer === Scorers.WeightedRatio -> PreparedScorer { c, cutoff -> weightedRatioCps(query, c, cutoff) }
    else -> null
}
