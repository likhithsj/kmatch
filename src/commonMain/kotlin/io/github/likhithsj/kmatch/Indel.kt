package io.github.likhithsj.kmatch

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
 * 0.1.0 uses a straightforward two-row LCS DP over code points. The Myers/Hyyrö
 * bit-parallel core replaces it in 0.2.0 behind the same frozen semantics.
 */

/** Length of the longest common subsequence of [a] and [b]. */
internal fun lcsLength(a: IntArray, b: IntArray): Int {
    if (a.isEmpty() || b.isEmpty()) return 0
    // Keep the DP row on the shorter sequence.
    val (s, l) = if (a.size <= b.size) a to b else b to a
    val row = IntArray(s.size + 1)
    for (i in l.indices) {
        val ci = l[i]
        var prevDiag = 0 // row[j] from the previous iteration of i
        for (j in s.indices) {
            val tmp = row[j + 1]
            row[j + 1] = if (ci == s[j]) prevDiag + 1 else maxOf(row[j + 1], row[j])
            prevDiag = tmp
        }
    }
    return row[s.size]
}

/** Indel distance. If [scoreCutoff] is non-null and the distance exceeds it,
 * returns `scoreCutoff + 1` instead (mirrors RapidFuzz). */
internal fun indelDistance(a: IntArray, b: IntArray, scoreCutoff: Int? = null): Int {
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
    val normSim = if (lensum == 0) 1.0 else 1.0 - (a.size + b.size - 2 * lcsLength(a, b)).toDouble() / lensum
    return if (scoreCutoff == null || normSim >= scoreCutoff) normSim else 0.0
}
