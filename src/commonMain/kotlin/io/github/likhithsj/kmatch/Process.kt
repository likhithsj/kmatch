package io.github.likhithsj.kmatch

/**
 * RapidFuzz's default preprocessing:
 *
 * 1. replaces every non-alphanumeric code point with a space,
 * 2. trims leading and trailing whitespace,
 * 3. lowercases.
 *
 * Bit-exact with `rapidfuzz.utils.default_process` (C++ backend): alphanumeric
 * classification and the simple per-code-point lowercase mapping come from
 * tables probed directly from the pinned RapidFuzz version, not from the host
 * platform's (differing) Unicode APIs. Unicode-aware: keeps accented letters,
 * non-Latin scripts, and numeric characters like `²`; underscore is replaced.
 */
public fun defaultProcess(s: String): String =
    buildStringFromCodePoints(defaultProcessCodePoints(s.toCodePoints()))

/** Code-point-level implementation of [defaultProcess]. */
internal fun defaultProcessCodePoints(cps: IntArray): IntArray {
    val mapped = IntArray(cps.size)
    for (i in cps.indices) {
        val cp = cps[i]
        mapped[i] = if (isWordCodePoint(cp)) lowerCodePoint(cp) else 0x20
    }
    // After substitution the only whitespace left is the plain space, so
    // trimming reduces to stripping 0x20 from both ends.
    var start = 0
    var end = mapped.size
    while (start < end && mapped[start] == 0x20) start++
    while (end > start && mapped[end - 1] == 0x20) end--
    return if (start == 0 && end == mapped.size) mapped else mapped.copyOfRange(start, end)
}
