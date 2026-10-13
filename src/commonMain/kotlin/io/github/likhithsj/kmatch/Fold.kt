package io.github.likhithsj.kmatch

/**
 * Folds accents and compatibility forms for diacritic-insensitive search:
 * `"São"` → `"Sao"`, `"Łódź"` → `"Lodz"`, `"ﬁ"` → `"fi"`,
 * full-width `"Ｔｏｋｙｏ"` → `"Tokyo"`, `"jalapeño"` → `"jalapeno"`.
 *
 * The fold is NFKD decomposition, dropping marks only from scripts where
 * marks are optional vocalization (Latin/Greek/Cyrillic combining accents,
 * Hebrew points and cantillation, Arabic harakat), plus an explicit fold of
 * the Latin letters that don't decompose (`ø`→`o`, `æ`→`ae`, `ß`→`ss`,
 * `ł`→`l`, `þ`→`th`, …). Indic and Southeast Asian marks are left intact —
 * there they ARE the vowels — and Hangul stays composed. Case is preserved.
 *
 * This is kmatch's own opt-in processor, NOT part of RapidFuzz parity:
 * RapidFuzz never folds, and no built-in scorer calls this unless you pass
 * it as the `processor`. For search you usually want [searchFold], which
 * also applies [defaultProcess].
 */
public fun stripAccents(s: String): String {
    val cps = s.toCodePoints()
    var changed = false
    for (cp in cps) {
        if (foldIndex(cp) >= 0) {
            changed = true
            break
        }
    }
    if (!changed) return s
    return buildString(s.length) {
        for (cp in cps) {
            val i = foldIndex(cp)
            if (i >= 0) {
                append(FOLD_DATA, FOLD_OFFSETS[i], FOLD_OFFSETS[i + 1])
            } else if (cp < 0x10000) {
                append(cp.toChar())
            } else {
                val c = cp - 0x10000
                append(((c shr 10) + 0xD800).toChar())
                append(((c and 0x3FF) + 0xDC00).toChar())
            }
        }
    }
}

/**
 * The processor for accent-insensitive search: [stripAccents] followed by
 * [defaultProcess] (lowercase, de-punctuate, trim). With it, `"caffe latte"`
 * matches `"Caffè Latte"` at 100 and `"jalapeno"` matches `"Jalapeño"`.
 *
 * ```kotlin
 * extractTop(query, items, keySelector = { it.name }, processor = ::searchFold)
 * ```
 */
public fun searchFold(s: String): String = defaultProcess(stripAccents(s))

/** Index into the fold tables, or -1 when [cp] folds to itself. */
private fun foldIndex(cp: Int): Int {
    var lo = 0
    var hi = FOLD_KEYS.size - 1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        when {
            cp < FOLD_KEYS[mid] -> hi = mid - 1
            cp > FOLD_KEYS[mid] -> lo = mid + 1
            else -> return mid
        }
    }
    return -1
}
