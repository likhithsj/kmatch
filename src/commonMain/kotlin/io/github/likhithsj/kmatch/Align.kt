package io.github.likhithsj.kmatch

/*
 * Alignment recovery for match highlighting.
 *
 * The matched segments of one optimal indel alignment (equivalently, one
 * longest common subsequence) are recovered with Hirschberg's divide-and-
 * conquer: O(len1 * len2) time but only linear memory, so long inputs are
 * safe. Alignment runs on code points; the public API reports UTF-16 char
 * indices, directly usable with substring / AnnotatedString highlighting.
 *
 * When several optimal alignments exist, the one found is deterministic but
 * otherwise unspecified -- like RapidFuzz's editops, callers must not depend
 * on which optimal alignment is chosen.
 */

/**
 * A maximal run of consecutive code points matched between the two strings:
 * `s1.substring(aStart, aStart + length) == s2.substring(bStart, bStart + length)`.
 *
 * Indices and [length] are UTF-16 char units into the original strings (the
 * matched text is identical in both, so its char length is too).
 */
public data class MatchingBlock(
    val aStart: Int,
    val bStart: Int,
    val length: Int,
)

/**
 * The matched segments of one optimal indel alignment between [s1] and [s2],
 * as maximal [MatchingBlock]s in ascending position order.
 *
 * The blocks cover exactly the longest common subsequence: deleting
 * everything outside them turns both strings into the same string. Useful
 * with [Fuzz.ratio]-style scores to show *why* two strings matched.
 */
public fun matchingBlocks(s1: String, s2: String): List<MatchingBlock> {
    val a = s1.toCodePoints()
    val b = s2.toCodePoints()
    if (a.isEmpty() || b.isEmpty()) return emptyList()

    val pairs = ArrayList<Int>() // flattened (i, j) matched cp index pairs
    hirschberg(a, 0, a.size, b, 0, b.size, pairs)

    // Char offset of each code point index (prefix sums of char widths).
    val offA = charOffsets(a)
    val offB = charOffsets(b)

    // Merge consecutive diagonal pairs into maximal blocks.
    val blocks = ArrayList<MatchingBlock>()
    var k = 0
    while (k < pairs.size) {
        val i0 = pairs[k]
        val j0 = pairs[k + 1]
        var len = 1
        while (k + 2 < pairs.size && pairs[k + 2] == i0 + len && pairs[k + 3] == j0 + len) {
            len++
            k += 2
        }
        blocks.add(MatchingBlock(offA[i0], offB[j0], offA[i0 + len] - offA[i0]))
        k += 2
    }
    return blocks
}

/**
 * The portions of [choice] matched by one optimal indel alignment against
 * [query], as char-index ranges in ascending order -- the highlighting
 * companion to extraction:
 *
 * ```kotlin
 * val ranges = matchingRanges("new york", best.choice)
 * // bold choice.substring(r.first, r.last + 1) for each range
 * ```
 */
public fun matchingRanges(query: String, choice: String): List<IntRange> =
    matchingBlocks(query, choice).map { it.bStart until it.bStart + it.length }

// ---------------------------------------------------------------------------

private fun charOffsets(cps: IntArray): IntArray {
    val off = IntArray(cps.size + 1)
    for (i in cps.indices) off[i + 1] = off[i] + if (cps[i] < 0x10000) 1 else 2
    return off
}

/** Appends matched (i, j) pairs of one LCS path, flattened, in order. */
private fun hirschberg(
    a: IntArray, aFrom: Int, aTo: Int,
    b: IntArray, bFrom: Int, bTo: Int,
    out: MutableList<Int>,
) {
    val m = aTo - aFrom
    if (m == 0 || bTo <= bFrom) return
    if (m == 1) {
        val c = a[aFrom]
        for (j in bFrom until bTo) {
            if (b[j] == c) {
                out.add(aFrom)
                out.add(j)
                return
            }
        }
        return
    }
    val mid = (aFrom + aTo) / 2
    val n = bTo - bFrom
    val f = IntArray(n + 1)
    val g = IntArray(n + 1)
    lcsRowForward(a, aFrom, mid, b, bFrom, bTo, f)
    lcsRowBackward(a, mid, aTo, b, bFrom, bTo, g)
    var split = 0
    var best = -1
    for (j in 0..n) {
        val v = f[j] + g[n - j]
        if (v > best) {
            best = v
            split = j
        }
    }
    hirschberg(a, aFrom, mid, b, bFrom, bFrom + split, out)
    hirschberg(a, mid, aTo, b, bFrom + split, bTo, out)
}

/** row[j] = LCS(a[aFrom until aTo), b[bFrom until bFrom + j)). */
private fun lcsRowForward(
    a: IntArray, aFrom: Int, aTo: Int,
    b: IntArray, bFrom: Int, bTo: Int,
    row: IntArray,
) {
    val n = bTo - bFrom
    row.fill(0, 0, n + 1)
    for (i in aFrom until aTo) {
        val ca = a[i]
        var prevDiag = 0
        for (j in 1..n) {
            val tmp = row[j]
            row[j] = if (ca == b[bFrom + j - 1]) prevDiag + 1 else maxOf(row[j], row[j - 1])
            prevDiag = tmp
        }
    }
}

/** row[j] = LCS(a[aFrom until aTo), b[bTo - j until bTo)). */
private fun lcsRowBackward(
    a: IntArray, aFrom: Int, aTo: Int,
    b: IntArray, bFrom: Int, bTo: Int,
    row: IntArray,
) {
    val n = bTo - bFrom
    row.fill(0, 0, n + 1)
    for (i in aTo - 1 downTo aFrom) {
        val ca = a[i]
        var prevDiag = 0
        for (j in 1..n) {
            val tmp = row[j]
            row[j] = if (ca == b[bTo - j]) prevDiag + 1 else maxOf(row[j], row[j - 1])
            prevDiag = tmp
        }
    }
}
