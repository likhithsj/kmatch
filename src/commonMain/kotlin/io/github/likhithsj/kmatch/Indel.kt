package io.github.likhithsj.kmatch

import kotlin.math.abs

/*
 * Indel distance: the minimum number of insertions and deletions required to
 * transform one sequence into the other (Levenshtein with substitution cost 2).
 *
 * distance(a, b) = len(a) + len(b) - 2 * LCS(a, b)
 *
 * This is RapidFuzz's `distance.Indel`, the basis of every `fuzz` scorer. Note
 * that `ratio` is therefore NOT normalized Levenshtein: ratio("myself", "me")
 * is 50 under indel, 33.3 under Levenshtein.
 *
 * LCS length is computed with Hyyrö's bit-parallel algorithm: the DP column is
 * packed into machine words, and each text character costs a handful of word
 * operations:
 *
 *     u = S & M[c]
 *     S = (S + u) | (S - u)
 *
 * where S starts all-ones and M[c] marks the positions of c in the pattern.
 * LCS = popcount(~S). `S - u` never borrows (u is a bitwise subset of S), and
 * carries from `S + u` that run past the pattern length are restored to 1 by
 * the OR, so only the addition needs cross-word carry in the blocked variant.
 */

/**
 * Match masks for a fixed pattern, reusable across many texts (one query
 * scanned against a whole collection, or one `partialRatio` window scan).
 *
 * Patterns of at most 64 code points use a single-word fast path; longer
 * patterns use a blocked variant with one word per 64 pattern positions.
 * ASCII masks live in a dense table, other code points in a hash map.
 *
 * Instances reuse an internal scratch buffer and are not thread-safe.
 */
internal class IndelPattern(pattern: IntArray, from: Int = 0, to: Int = pattern.size) {

    val length: Int = to - from
    private val words: Int = if (length == 0) 0 else (length + 63) ushr 6

    // Single-word masks (words == 1).
    private val ascii1 = if (words == 1) LongArray(128) else EMPTY_LONGS
    private var other1: HashMap<Int, Long>? = null

    // Blocked masks (words > 1), laid out per code point: [word0, word1, ...].
    private val asciiN = if (words > 1) LongArray(128 * words) else EMPTY_LONGS
    private var otherN: HashMap<Int, LongArray>? = null

    // Scratch DP column for the blocked path.
    private val scratch = if (words > 1) LongArray(words) else EMPTY_LONGS

    init {
        if (words == 1) {
            for (i in from until to) {
                val cp = pattern[i]
                val bit = 1L shl (i - from)
                if (cp < 128) {
                    ascii1[cp] = ascii1[cp] or bit
                } else {
                    val map = other1 ?: HashMap<Int, Long>().also { other1 = it }
                    map[cp] = (map[cp] ?: 0L) or bit
                }
            }
        } else if (words > 1) {
            for (i in from until to) {
                val cp = pattern[i]
                val pos = i - from
                val word = pos ushr 6
                val bit = 1L shl (pos and 63)
                if (cp < 128) {
                    asciiN[cp * words + word] = asciiN[cp * words + word] or bit
                } else {
                    val map = otherN ?: HashMap<Int, LongArray>().also { otherN = it }
                    val m = map.getOrPut(cp) { LongArray(words) }
                    m[word] = m[word] or bit
                }
            }
        }
    }

    /** Whether [cp] occurs in the pattern at all. */
    operator fun contains(cp: Int): Boolean = when {
        words == 0 -> false
        words == 1 -> (if (cp < 128) ascii1[cp] else other1?.get(cp) ?: 0L) != 0L
        cp < 128 -> {
            val base = cp * words
            var any = 0L
            for (w in 0 until words) any = any or asciiN[base + w]
            any != 0L
        }
        else -> otherN?.get(cp) != null
    }

    /** LCS length between the pattern and `text[from until to)`. */
    fun lcs(text: IntArray, from: Int = 0, to: Int = text.size): Int {
        if (length == 0 || to <= from) return 0
        return if (words == 1) lcsSingle(text, from, to) else lcsBlocked(text, from, to)
    }

    private fun lcsSingle(text: IntArray, from: Int, to: Int): Int {
        val ascii = ascii1
        val other = other1
        var s = -1L
        for (i in from until to) {
            val cp = text[i]
            val m = if (cp < 128) ascii[cp] else other?.get(cp) ?: 0L
            if (m != 0L) {
                val u = s and m
                s = (s + u) or (s - u)
            }
        }
        return s.inv().countOneBits()
    }

    private fun lcsBlocked(text: IntArray, from: Int, to: Int): Int {
        val words = words
        val s = scratch
        s.fill(-1L)
        for (i in from until to) {
            val cp = text[i]
            if (cp < 128) {
                val base = cp * words
                var carry = 0L
                for (w in 0 until words) {
                    val sw = s[w]
                    val u = sw and asciiN[base + w]
                    val t1 = sw + u
                    val t2 = t1 + carry
                    carry = if (t1.toULong() < sw.toULong() || t2.toULong() < t1.toULong()) 1L else 0L
                    s[w] = t2 or (sw - u)
                }
            } else {
                val m = otherN?.get(cp) ?: continue
                var carry = 0L
                for (w in 0 until words) {
                    val sw = s[w]
                    val u = sw and m[w]
                    val t1 = sw + u
                    val t2 = t1 + carry
                    carry = if (t1.toULong() < sw.toULong() || t2.toULong() < t1.toULong()) 1L else 0L
                    s[w] = t2 or (sw - u)
                }
            }
        }
        var lcs = 0
        for (w in 0 until words) lcs += s[w].inv().countOneBits()
        return lcs
    }

    private companion object {
        val EMPTY_LONGS = LongArray(0)
    }
}

/** Length of the longest common subsequence of [a] and [b]. */
internal fun lcsLength(a: IntArray, b: IntArray): Int {
    if (a.isEmpty() || b.isEmpty()) return 0
    // Build masks over the shorter sequence: fewer words per text character.
    return if (a.size <= b.size) IndelPattern(a).lcs(b) else IndelPattern(b).lcs(a)
}

/** Indel distance. If [scoreCutoff] is non-null and the distance exceeds it,
 * returns `scoreCutoff + 1` instead (mirrors RapidFuzz). */
internal fun indelDistance(a: IntArray, b: IntArray, scoreCutoff: Int? = null): Int {
    // dist >= |len(a) - len(b)| always; skip the LCS when that already misses.
    if (scoreCutoff != null && abs(a.size - b.size) > scoreCutoff) return scoreCutoff + 1
    val dist = a.size + b.size - 2 * lcsLength(a, b)
    return if (scoreCutoff == null || dist <= scoreCutoff) dist else scoreCutoff + 1
}

/**
 * Normalized indel similarity in [0, 1]: `1 - distance / (len(a) + len(b))`.
 * Two empty sequences are perfectly similar (1.0).
 * If [scoreCutoff] is non-null and the similarity is below it, returns 0.0
 * (mirrors RapidFuzz's score_cutoff contract).
 */
internal fun indelNormalizedSimilarity(a: IntArray, b: IntArray, scoreCutoff: Double? = null): Double {
    val lensum = a.size + b.size
    // Two empties are perfectly similar -- but the cutoff still applies
    // (RapidFuzz returns 0 for score_cutoff > 1 even here).
    if (lensum == 0) return if (scoreCutoff == null || 1.0 >= scoreCutoff) 1.0 else 0.0
    // normSim <= 1 - |len(a) - len(b)| / lensum; skip the LCS when even that
    // upper bound misses the cutoff. The bound uses the same arithmetic shape
    // as the exact score, so boundary cases agree with the full computation.
    if (scoreCutoff != null && 1.0 - abs(a.size - b.size).toDouble() / lensum < scoreCutoff) return 0.0
    val normSim = 1.0 - (a.size + b.size - 2 * lcsLength(a, b)).toDouble() / lensum
    return if (scoreCutoff == null || normSim >= scoreCutoff) normSim else 0.0
}

/**
 * Normalized indel similarity between [pattern]'s underlying sequence and
 * `text[from until to)`, reusing prebuilt match masks. Semantics identical to
 * [indelNormalizedSimilarity].
 */
internal fun indelNormalizedSimilarity(
    pattern: IndelPattern,
    text: IntArray,
    from: Int,
    to: Int,
    scoreCutoff: Double? = null,
): Double {
    val textLen = to - from
    val lensum = pattern.length + textLen
    if (lensum == 0) return if (scoreCutoff == null || 1.0 >= scoreCutoff) 1.0 else 0.0
    if (scoreCutoff != null && 1.0 - abs(pattern.length - textLen).toDouble() / lensum < scoreCutoff) return 0.0
    val normSim = 1.0 - (lensum - 2 * pattern.lcs(text, from, to)).toDouble() / lensum
    return if (scoreCutoff == null || normSim >= scoreCutoff) normSim else 0.0
}
