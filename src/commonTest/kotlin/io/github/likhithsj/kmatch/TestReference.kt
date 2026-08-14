package io.github.likhithsj.kmatch

/**
 * Reference implementation: two-row LCS DP over code points -- the exact
 * 0.1.0 shipping implementation, kept in test sources as the ground truth the
 * bit-parallel core is proven equivalent to (and benchmarked against).
 */
internal fun lcsLengthDp(a: IntArray, b: IntArray): Int {
    if (a.isEmpty() || b.isEmpty()) return 0
    val (s, l) = if (a.size <= b.size) a to b else b to a
    val row = IntArray(s.size + 1)
    for (i in l.indices) {
        val ci = l[i]
        var prevDiag = 0
        for (j in s.indices) {
            val tmp = row[j + 1]
            row[j + 1] = if (ci == s[j]) prevDiag + 1 else maxOf(row[j + 1], row[j])
            prevDiag = tmp
        }
    }
    return row[s.size]
}
