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

    // --- Regressions found by tools/differential-fuzz against 1.0.0 ----------

    @Test
    fun aScoreFedBackAsTheCutoffKeepsTheMatch() {
        // 1.0.0 returned 0.0 here: it compared on the 0-1 scale, where
        // 83.33333333333334 / 100 lands one ulp above the window's 5/6.
        // RapidFuzz gates full-length windows on the 0-100 scale.
        val score = Fuzz.partialRatio("abbabbaabba", "bababa")
        assertEquals(83.33333333333334, score)
        assertEquals(score, Fuzz.partialRatio("abbabbaabba", "bababa", scoreCutoff = score))

        // Same through everything built on partialRatio.
        val w = Fuzz.weightedRatio("abbabbaabba", "bababa")
        assertEquals(75.00000000000001, w)
        assertEquals(w, Fuzz.weightedRatio("abbabbaabba", "bababa", scoreCutoff = w))

        // ...and through extraction: "at least as good as the best" must
        // still contain the best.
        val choices = listOf("xyz", "bababa", "qqq")
        val best = extractOne("abbabbaabba", choices, scorer = Scorers.PartialRatio)!!
        val atLeast = extractAll("abbabbaabba", choices, scorer = Scorers.PartialRatio, scoreCutoff = best.score)
        assertEquals(listOf(best.index), atLeast.map { it.index })
    }

    @Test
    fun cutoffOneUlpAboveTheScoreFollowsTheBackend() {
        // Not a typo: the backend gates prefix/suffix windows on the 0-1
        // scale, so some scores survive a cutoff one ulp above them. Parity
        // means reproducing that, not "fixing" it.
        assertEquals(54.54545454545454, Fuzz.partialRatio("aaaacabaaac", "babccb"))
        assertEquals(54.54545454545454, Fuzz.partialRatio("aaaacabaaac", "babccb", scoreCutoff = 54.54545454545455))
        assertEquals(0.0, Fuzz.partialRatio("abbabbaabba", "bababa", scoreCutoff = 83.33333333333336))
    }

    @Test
    fun nbspSeparatesTokensOnlyInStringsWithWideCharacters() {
        // RapidFuzz's tokenizer picks its separator table by string storage
        // width. "Zürich HB" fits in one byte per character: NBSP is an
        // ordinary character there.
        assertEquals(66.66666666666667, Fuzz.tokenSortRatio("Z\u00fcrich\u00a0HB", "HB Z\u00fcrich"))
        // "Łódź" does not (Ł is U+0141): now NBSP separates.
        assertEquals(100.0, Fuzz.tokenSortRatio("\u0141\u00f3d\u017a\u00a0Kaliska", "Kaliska \u0141\u00f3d\u017a"))
        // The width is each string's own: only s1 is wide here.
        assertEquals(75.0, Fuzz.tokenSortRatio("b\u00a0a \u0100", "a b"))
        assertEquals(25.0, Fuzz.tokenSortRatio("b\u00a0a", "a b \u0100"))
        // NEL (U+0085) behaves the same way.
        assertEquals(100.0, Fuzz.tokenSetRatio("b\u0085a \u0100", "a b \u0100"))
    }
}
