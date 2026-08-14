package io.github.likhithsj.kmatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Hand-written sanity checks; exhaustive parity lives in [GoldenVectorTest]. */
class FuzzTest {

    @Test
    fun ratioIsIndelNotLevenshtein() {
        // LCS("myself", "me") = 2, indel dist = 6 + 2 - 2*2 = 4, sim = 1 - 4/8 = 0.5.
        // Normalized Levenshtein would give 33.3 -- the classic porting error.
        assertEquals(50.0, Fuzz.ratio("myself", "me"))
    }

    @Test
    fun emptyStringSemantics() {
        assertEquals(100.0, Fuzz.ratio("", ""))
        assertEquals(0.0, Fuzz.ratio("", "a"))
        assertEquals(100.0, Fuzz.partialRatio("", ""))
        assertEquals(0.0, Fuzz.partialRatio("", "a"))
        assertEquals(0.0, Fuzz.quickRatio("", ""))
        assertEquals(0.0, Fuzz.weightedRatio("", ""))
        assertEquals(0.0, Fuzz.tokenSetRatio("", ""))
    }

    @Test
    fun scoreCutoffZeroesResultsBelowIt() {
        val uncut = Fuzz.ratio("hello world", "hello world!")
        assertTrue(uncut > 90.0 && uncut < 99.0)
        assertEquals(uncut, Fuzz.ratio("hello world", "hello world!", scoreCutoff = 90.0))
        assertEquals(0.0, Fuzz.ratio("hello world", "hello world!", scoreCutoff = 99.0))
        assertEquals(0.0, Fuzz.partialRatio("abcd", "wxyz", scoreCutoff = 10.0))
        assertEquals(0.0, Fuzz.weightedRatio("abcd", "axyz", scoreCutoff = 99.0))
    }

    @Test
    fun codePointSemanticsForAstralCharacters() {
        // One emoji is one symbol: replacing it is 2 indel edits out of 2+2.
        assertEquals(0.0, Fuzz.ratio("😀", "😄")) // 😀 vs 😄
        assertEquals(50.0, Fuzz.ratio("a😀", "a😄"))
    }

    @Test
    fun tokenSortHandlesAstralOrdering() {
        // Sorting must compare code points, not UTF-16 units: U+FFFD < U+1F600.
        assertEquals(100.0, Fuzz.tokenSortRatio("😀 �", "� 😀"))
    }

    @Test
    fun defaultProcessBasics() {
        // Internal whitespace is NOT collapsed: comma and space each become a space.
        assertEquals("hello  world", defaultProcess("Hello, World!"))
        assertEquals("snake case name", defaultProcess("snake_case_name"))
        assertEquals("", defaultProcess("!!! ???"))
    }
}
