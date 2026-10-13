package io.github.likhithsj.kmatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class FoldTest {

    @Test
    fun latinAccentsFold() {
        assertEquals("Sao Paulo", stripAccents("São Paulo"))
        assertEquals("Zurich", stripAccents("Zürich"))
        assertEquals("jalapeno", stripAccents("jalapeño"))
        assertEquals("Caffe Latte", stripAccents("Caffè Latte"))
        assertEquals("Cordoba", stripAccents("Córdoba"))
        assertEquals("Reykjavik", stripAccents("Reykjavík"))
    }

    @Test
    fun nonDecomposableLatinLettersFold() {
        assertEquals("Lodz", stripAccents("Łódź"))
        assertEquals("Orsted", stripAccents("Ørsted"))
        assertEquals("strasse", stripAccents("straße"))
        assertEquals("AEroskobing", stripAccents("Ærosköbing"))
        assertEquals("thorn", stripAccents("þorn"))
        assertEquals("Dordur", stripAccents("Ðörður"))
        assertEquals("istanbul", stripAccents("ıstanbul"))
    }

    @Test
    fun alreadyDecomposedMarksAreDropped() {
        // "é" as e + combining acute: the standalone mark folds to "".
        assertEquals("e", stripAccents("é"))
    }

    @Test
    fun hebrewPointsAndArabicHarakatFold() {
        // שָׁלוֹם with niqqud -> bare letters (the shin dot U+05C1 is a point).
        assertEquals("שלום", stripAccents("שָׁלוֹם"))
        // مَرْحَبًا with harakat -> bare letters.
        assertEquals("مرحبا", stripAccents("مَرْحَبًا"))
    }

    @Test
    fun compatibilityFormsFold() {
        assertEquals("Tokyo", stripAccents("Ｔｏｋｙｏ"))   // full-width
        assertEquals("fi", stripAccents("ﬁ"))                              // fi ligature
        assertEquals("1 2", stripAccents("1 2"))
    }

    @Test
    fun protectedScriptsAreUntouched() {
        val hindi = "हिन्दी"      // हिन्दी: matras are vowels
        assertSame(hindi, stripAccents(hindi))
        val hangul = "한국어"                        // 한국어 stays composed
        assertSame(hangul, stripAccents(hangul))
        val emoji = "caffe 🌟"                         // astral pass-through
        assertSame(emoji, stripAccents(emoji))
    }

    @Test
    fun casePreservedByStripAccentsLoweredBySearchFold() {
        assertEquals("SAO", stripAccents("SÃO"))
        assertEquals("sao", searchFold("SÃO"))
        // defaultProcess trims the ends but (like RapidFuzz) keeps internal runs.
        assertEquals("caffe  latte", searchFold("  Caffè  Latte! "))
    }

    @Test
    fun searchFoldMakesUnaccentedQueriesExactMatches() {
        assertEquals(100.0, Fuzz.ratio("caffe latte", "Caffè Latte", processor = ::searchFold))
        assertEquals(100.0, Fuzz.ratio("jalapeno", "Jalapeño", processor = ::searchFold))
        val hit = extractOne("sao paulo", listOf("São Paulo", "San Diego"), processor = ::searchFold)
        assertEquals("São Paulo", hit?.choice)
        assertEquals(100.0, hit?.score)
    }

    @Test
    fun foldTablesAreWellFormed() {
        assertEquals(FOLD_KEYS.size + 1, FOLD_OFFSETS.size)
        assertEquals(FOLD_DATA.length, FOLD_OFFSETS.last())
        for (i in 1 until FOLD_KEYS.size) {
            check(FOLD_KEYS[i - 1] < FOLD_KEYS[i]) { "FOLD_KEYS not strictly sorted at $i" }
            check(FOLD_OFFSETS[i] <= FOLD_OFFSETS[i + 1]) { "FOLD_OFFSETS not monotonic at $i" }
        }
    }
}
