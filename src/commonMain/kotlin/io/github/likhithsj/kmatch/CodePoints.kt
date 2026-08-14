package io.github.likhithsj.kmatch

/**
 * Converts this string to an array of Unicode code points.
 *
 * All kmatch algorithms operate on code points, never on UTF-16 chars, matching
 * Python string semantics: a single emoji is one symbol, not two code units.
 * Unpaired surrogates are kept as their own (invalid) code point values.
 */
internal fun String.toCodePoints(): IntArray {
    val out = IntArray(length)
    var n = 0
    var i = 0
    while (i < length) {
        val hi = this[i]
        val cp: Int
        if (hi.isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) {
            cp = (hi.code - 0xD800 shl 10) + (this[i + 1].code - 0xDC00) + 0x10000
            i += 2
        } else {
            cp = hi.code
            i += 1
        }
        out[n++] = cp
    }
    return if (n == out.size) out else out.copyOf(n)
}

/** Converts an array of Unicode code points back to a string. */
internal fun buildStringFromCodePoints(cps: IntArray): String = buildString(cps.size) {
    for (cp in cps) appendCodePointCompat(cp)
}

internal fun StringBuilder.appendCodePointCompat(cp: Int) {
    if (cp < 0x10000) {
        append(cp.toChar())
    } else {
        val v = cp - 0x10000
        append(((v shr 10) + 0xD800).toChar())
        append(((v and 0x3FF) + 0xDC00).toChar())
    }
}

/** Lexicographic code-point comparison. Matches Python's string ordering, which
 * differs from UTF-16 [String] ordering for astral-plane characters. */
internal object CodePointArrayComparator : Comparator<IntArray> {
    override fun compare(a: IntArray, b: IntArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            if (a[i] != b[i]) return a[i].compareTo(b[i])
        }
        return a.size.compareTo(b.size)
    }
}
