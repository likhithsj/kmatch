package io.github.likhithsj.kmatch

import kotlin.math.ceil
import kotlin.math.max

/**
 * RapidFuzz-compatible fuzzy string scorers.
 *
 * Every function returns exactly what the corresponding `rapidfuzz.fuzz`
 * function returns (0-100 scale, `Double`), verified against golden vectors
 * generated from a pinned RapidFuzz version. Any change to an output here is a
 * breaking change by definition.
 *
 * All scorers accept an optional [processor] applied to both inputs first
 * (pass [defaultProcess] for RapidFuzz's default preprocessing) and an
 * optional [scoreCutoff]: when the result would fall below it, 0.0 is returned
 * instead.
 */
public object Fuzz {

    /**
     * Normalized indel similarity in the range 0-100.
     *
     * Based on insertions and deletions only -- this is **not** normalized
     * Levenshtein: `ratio("myself", "me")` is 50 under indel, 33.3 under
     * Levenshtein. This matches RapidFuzz and fuzzywuzzy semantics and is the
     * single most common porting error.
     */
    public fun ratio(
        s1: String,
        s2: String,
        processor: ((String) -> String)? = null,
        scoreCutoff: Double? = null,
    ): Double {
        val (a, b) = preprocess(s1, s2, processor)
        return ratioCps(a, b, scoreCutoff ?: 0.0)
    }

    /**
     * Searches for the optimal alignment of the shorter string within the
     * longer string and returns the [ratio] for that alignment.
     */
    public fun partialRatio(
        s1: String,
        s2: String,
        processor: ((String) -> String)? = null,
        scoreCutoff: Double? = null,
    ): Double {
        val (a, b) = preprocess(s1, s2, processor)
        return partialRatioCps(a, b, scoreCutoff ?: 0.0)
    }

    /** Sorts the words in both strings and calculates the [ratio] between them. */
    public fun tokenSortRatio(
        s1: String,
        s2: String,
        processor: ((String) -> String)? = null,
        scoreCutoff: Double? = null,
    ): Double {
        val (a, b) = preprocess(s1, s2, processor)
        return tokenSortRatioCps(a, b, scoreCutoff ?: 0.0)
    }

    /**
     * Compares the words in the strings based on unique and common words
     * between them using [ratio]. Returns 100 if the tokens of one string are
     * a subset of the other's, regardless of extra content in the longer one.
     */
    public fun tokenSetRatio(
        s1: String,
        s2: String,
        processor: ((String) -> String)? = null,
        scoreCutoff: Double? = null,
    ): Double {
        val (a, b) = preprocess(s1, s2, processor)
        return tokenSetRatioCps(a, b, scoreCutoff ?: 0.0)
    }

    /** Maximum of [tokenSetRatio] and [tokenSortRatio]. */
    public fun tokenRatio(
        s1: String,
        s2: String,
        processor: ((String) -> String)? = null,
        scoreCutoff: Double? = null,
    ): Double {
        val (a, b) = preprocess(s1, s2, processor)
        return tokenRatioCps(a, b, scoreCutoff ?: 0.0)
    }

    /** Sorts the words in both strings and calculates the [partialRatio] between them. */
    public fun partialTokenSortRatio(
        s1: String,
        s2: String,
        processor: ((String) -> String)? = null,
        scoreCutoff: Double? = null,
    ): Double {
        val (a, b) = preprocess(s1, s2, processor)
        return partialTokenSortRatioCps(a, b, scoreCutoff ?: 0.0)
    }

    /**
     * Compares the words in the strings based on unique and common words
     * between them using [partialRatio].
     */
    public fun partialTokenSetRatio(
        s1: String,
        s2: String,
        processor: ((String) -> String)? = null,
        scoreCutoff: Double? = null,
    ): Double {
        val (a, b) = preprocess(s1, s2, processor)
        return partialTokenSetRatioCps(a, b, scoreCutoff ?: 0.0)
    }

    /** Maximum of [partialTokenSetRatio] and [partialTokenSortRatio]. */
    public fun partialTokenRatio(
        s1: String,
        s2: String,
        processor: ((String) -> String)? = null,
        scoreCutoff: Double? = null,
    ): Double {
        val (a, b) = preprocess(s1, s2, processor)
        return partialTokenRatioCps(a, b, scoreCutoff ?: 0.0)
    }

    /**
     * Weighted combination of the other ratios (RapidFuzz `WRatio`), including
     * its exact internal constants: 0.95 unbase scale, 0.9/0.6 partial scale,
     * and the 1.5 / 8.0 length-ratio thresholds.
     */
    public fun weightedRatio(
        s1: String,
        s2: String,
        processor: ((String) -> String)? = null,
        scoreCutoff: Double? = null,
    ): Double {
        val (a, b) = preprocess(s1, s2, processor)
        return weightedRatioCps(a, b, scoreCutoff ?: 0.0)
    }

    /**
     * Quick ratio (RapidFuzz `QRatio`): behaves like [ratio] except that it
     * returns 0 when either string is empty (after processing).
     */
    public fun quickRatio(
        s1: String,
        s2: String,
        processor: ((String) -> String)? = null,
        scoreCutoff: Double? = null,
    ): Double {
        val (a, b) = preprocess(s1, s2, processor)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return ratioCps(a, b, scoreCutoff ?: 0.0)
    }

    private fun preprocess(
        s1: String,
        s2: String,
        processor: ((String) -> String)?,
    ): Pair<IntArray, IntArray> {
        val a = if (processor != null) processor(s1) else s1
        val b = if (processor != null) processor(s2) else s2
        return a.toCodePoints() to b.toCodePoints()
    }
}

// ---------------------------------------------------------------------------
// Code-point-level implementations. These mirror RapidFuzz's fuzz module
// statement by statement; scoreCutoff is on the 0-100 scale, 0.0 deactivates.
// ---------------------------------------------------------------------------

internal fun ratioCps(a: IntArray, b: IntArray, scoreCutoff: Double): Double =
    indelNormalizedSimilarity(a, b, scoreCutoff / 100.0) * 100.0

/** `100 - 100 * dist / lensum`, or 100 for empty inputs; 0 below the cutoff. */
private fun normDistance(dist: Int, lensum: Int, scoreCutoff: Double): Double {
    val score = if (lensum > 0) 100.0 - 100.0 * dist / lensum else 100.0
    return if (score >= scoreCutoff) score else 0.0
}

/**
 * Mirrors RapidFuzz's `_partial_ratio_impl`: scans every window of `longer`
 * that could yield an optimal alignment of `shorter`, skipping windows whose
 * boundary character does not occur in `shorter` at all. Assumes
 * `shorter.size <= longer.size`. Cutoff is on the 0-1 scale here.
 */
private fun partialRatioImpl(
    shorter: IntArray,
    longer: IntArray,
    scoreCutoffIn: Double,
    // Match masks are built once and reused for every window scan; callers
    // scanning a collection can pass a prebuilt pattern for the query side.
    pattern: IndelPattern = IndelPattern(shorter),
): Double {
    val len1 = shorter.size
    val len2 = longer.size
    if (len1 == 0) return 0.0

    var scoreCutoff = scoreCutoffIn
    var best = 0.0

    // Windows hanging off the start: longer[0 until i).
    for (i in 1 until len1) {
        if (longer[i - 1] !in pattern) continue
        val sim = indelNormalizedSimilarity(pattern, longer, 0, i, scoreCutoff)
        if (sim > best) {
            best = sim
            scoreCutoff = sim
            if (sim == 1.0) return 100.0
        }
    }

    // Full-length windows: longer[i until i + len1).
    for (i in 0 until len2 - len1) {
        if (longer[i + len1 - 1] !in pattern) continue
        val sim = indelNormalizedSimilarity(pattern, longer, i, i + len1, scoreCutoff)
        if (sim > best) {
            best = sim
            scoreCutoff = sim
            if (sim == 1.0) return 100.0
        }
    }

    // Windows hanging off the end: longer[i until len2).
    for (i in len2 - len1 until len2) {
        if (longer[i] !in pattern) continue
        val sim = indelNormalizedSimilarity(pattern, longer, i, len2, scoreCutoff)
        if (sim > best) {
            best = sim
            scoreCutoff = sim
            if (sim == 1.0) return 100.0
        }
    }

    return best * 100.0
}

internal fun partialRatioCps(a: IntArray, b: IntArray, scoreCutoffIn: Double): Double {
    var scoreCutoff = scoreCutoffIn
    if (a.isEmpty() && b.isEmpty()) return 100.0

    val len1 = a.size
    val len2 = b.size
    val shorter = if (len1 <= len2) a else b
    val longer = if (len1 <= len2) b else a

    var res = partialRatioImpl(shorter, longer, scoreCutoff / 100.0)
    if (res != 100.0 && len1 == len2) {
        scoreCutoff = max(scoreCutoff, res)
        val res2 = partialRatioImpl(longer, shorter, scoreCutoff / 100.0)
        if (res2 > res) res = res2
    }

    return if (res < scoreCutoff) 0.0 else res
}

/**
 * [partialRatioCps] with prebuilt match masks for [q] (the query side).
 * Reuse only applies while the query is the shorter side; other shapes fall
 * back to the regular computation, keeping semantics identical.
 */
internal fun partialRatioCpsCached(
    qPattern: IndelPattern,
    q: IntArray,
    c: IntArray,
    scoreCutoffIn: Double,
): Double {
    if (q.isEmpty() && c.isEmpty()) return 100.0
    if (q.size > c.size) return partialRatioCps(q, c, scoreCutoffIn)

    var scoreCutoff = scoreCutoffIn
    var res = partialRatioImpl(q, c, scoreCutoff / 100.0, qPattern)
    if (res != 100.0 && q.size == c.size) {
        scoreCutoff = max(scoreCutoff, res)
        val res2 = partialRatioImpl(c, q, scoreCutoff / 100.0)
        if (res2 > res) res = res2
    }
    return if (res < scoreCutoff) 0.0 else res
}

// --- tokenization ----------------------------------------------------------

/** Splits on the tokenizer whitespace table, dropping empty tokens. */
internal fun splitTokens(cps: IntArray): List<List<Int>> {
    val tokens = ArrayList<List<Int>>()
    var current: ArrayList<Int>? = null
    for (cp in cps) {
        if (isSpaceCodePoint(cp)) {
            if (current != null) {
                tokens.add(current)
                current = null
            }
        } else {
            (current ?: ArrayList<Int>().also { current = it }).add(cp)
        }
    }
    if (current != null) tokens.add(current)
    return tokens
}

private val TOKEN_COMPARATOR: Comparator<List<Int>> = Comparator { x, y ->
    val n = minOf(x.size, y.size)
    for (i in 0 until n) {
        if (x[i] != y[i]) return@Comparator x[i].compareTo(y[i])
    }
    x.size.compareTo(y.size)
}

/** Joins tokens with a single space (U+0020). */
private fun joinTokens(tokens: Collection<List<Int>>): IntArray {
    if (tokens.isEmpty()) return IntArray(0)
    var size = tokens.size - 1
    for (t in tokens) size += t.size
    val out = IntArray(size)
    var n = 0
    for (t in tokens) {
        if (n > 0) out[n++] = 0x20
        for (cp in t) out[n++] = cp
    }
    return out
}

internal fun sortJoin(tokens: Collection<List<Int>>): IntArray =
    joinTokens(tokens.sortedWith(TOKEN_COMPARATOR))

/** Length of the joined form without materializing it. */
private fun joinedLength(tokens: Collection<List<Int>>): Int {
    if (tokens.isEmpty()) return 0
    var size = tokens.size - 1
    for (t in tokens) size += t.size
    return size
}

// --- token scorers ---------------------------------------------------------

internal fun tokenSortRatioCps(a: IntArray, b: IntArray, scoreCutoff: Double): Double =
    ratioCps(sortJoin(splitTokens(a)), sortJoin(splitTokens(b)), scoreCutoff)

internal fun tokenSetRatioCps(a: IntArray, b: IntArray, scoreCutoff: Double): Double {
    val tokensA = splitTokens(a).toSet()
    val tokensB = splitTokens(b).toSet()

    // FuzzyWuzzy compatibility: empty token sets score 0.
    if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

    val intersect = tokensA intersect tokensB
    val diffAb = tokensA subtract tokensB
    val diffBa = tokensB subtract tokensA

    // One sentence's tokens are a subset of the other's.
    if (intersect.isNotEmpty() && (diffAb.isEmpty() || diffBa.isEmpty())) return 100.0

    val diffAbJoined = sortJoin(diffAb)
    val diffBaJoined = sortJoin(diffBa)

    val abLen = diffAbJoined.size
    val baLen = diffBaJoined.size
    val sectLen = joinedLength(intersect)

    // String lengths of sect+ab <-> sect and sect+ba <-> sect.
    val sectAbLen = sectLen + (if (sectLen != 0) 1 else 0) + abLen
    val sectBaLen = sectLen + (if (sectLen != 0) 1 else 0) + baLen

    var result = 0.0
    val cutoffDistance = ceil((sectAbLen + sectBaLen) * (1.0 - scoreCutoff / 100.0)).toInt()
    val dist = indelDistance(diffAbJoined, diffBaJoined, cutoffDistance)
    if (dist <= cutoffDistance) {
        result = normDistance(dist, sectAbLen + sectBaLen, scoreCutoff)
    }

    // Exit early since the other ratios are 0.
    if (sectLen == 0) return result

    // The distances sect+ab <-> sect and sect+ba <-> sect follow directly from
    // the length difference, since sect is a prefix of both sides.
    val sectAbRatio = normDistance(1 + abLen, sectLen + sectAbLen, scoreCutoff)
    val sectBaRatio = normDistance(1 + baLen, sectLen + sectBaLen, scoreCutoff)

    return maxOf(result, sectAbRatio, sectBaRatio)
}

internal fun tokenRatioCps(a: IntArray, b: IntArray, scoreCutoff: Double): Double =
    max(tokenSetRatioCps(a, b, scoreCutoff), tokenSortRatioCps(a, b, scoreCutoff))

internal fun partialTokenSortRatioCps(a: IntArray, b: IntArray, scoreCutoff: Double): Double =
    partialRatioCps(sortJoin(splitTokens(a)), sortJoin(splitTokens(b)), scoreCutoff)

internal fun partialTokenSetRatioCps(a: IntArray, b: IntArray, scoreCutoff: Double): Double {
    val tokensA = splitTokens(a).toSet()
    val tokensB = splitTokens(b).toSet()

    // FuzzyWuzzy compatibility: empty token sets score 0.
    if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0

    // Exit early when there is a common word in both sequences.
    if ((tokensA intersect tokensB).isNotEmpty()) return 100.0

    return partialRatioCps(sortJoin(tokensA subtract tokensB), sortJoin(tokensB subtract tokensA), scoreCutoff)
}

internal fun partialTokenRatioCps(a: IntArray, b: IntArray, scoreCutoffIn: Double): Double {
    var scoreCutoff = scoreCutoffIn
    val tokensSplitA = splitTokens(a)
    val tokensSplitB = splitTokens(b)
    val tokensA = tokensSplitA.toSet()
    val tokensB = tokensSplitB.toSet()

    // Exit early when there is a common word in both sequences.
    if ((tokensA intersect tokensB).isNotEmpty()) return 100.0

    val diffAb = tokensA subtract tokensB
    val diffBa = tokensB subtract tokensA

    val result = partialRatioCps(sortJoin(tokensSplitA), sortJoin(tokensSplitB), scoreCutoff)

    // Do not calculate the same partial_ratio twice.
    if (tokensSplitA.size == diffAb.size && tokensSplitB.size == diffBa.size) {
        return result
    }

    scoreCutoff = max(scoreCutoff, result)
    return max(result, partialRatioCps(sortJoin(diffAb), sortJoin(diffBa), scoreCutoff))
}

internal fun weightedRatioCps(a: IntArray, b: IntArray, scoreCutoffIn: Double): Double {
    val UNBASE_SCALE = 0.95

    // FuzzyWuzzy compatibility: empty strings score 0.
    if (a.isEmpty() || b.isEmpty()) return 0.0

    var scoreCutoff = scoreCutoffIn
    val len1 = a.size
    val len2 = b.size
    val lenRatio = if (len1 > len2) len1.toDouble() / len2 else len2.toDouble() / len1

    var endRatio = ratioCps(a, b, scoreCutoff)

    if (lenRatio < 1.5) {
        scoreCutoff = max(scoreCutoff, endRatio) / UNBASE_SCALE
        return max(endRatio, tokenRatioCps(a, b, scoreCutoff) * UNBASE_SCALE)
    }

    val PARTIAL_SCALE = if (lenRatio <= 8.0) 0.9 else 0.6
    scoreCutoff = max(scoreCutoff, endRatio) / PARTIAL_SCALE
    endRatio = max(endRatio, partialRatioCps(a, b, scoreCutoff) * PARTIAL_SCALE)

    scoreCutoff = max(scoreCutoff, endRatio) / UNBASE_SCALE
    return max(endRatio, partialTokenRatioCps(a, b, scoreCutoff) * UNBASE_SCALE * PARTIAL_SCALE)
}
