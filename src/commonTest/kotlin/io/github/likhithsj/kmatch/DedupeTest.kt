package io.github.likhithsj.kmatch

import kotlin.test.Test
import kotlin.test.assertEquals

class DedupeTest {

    @Test
    fun collapsesNearDuplicates() {
        // The canonical fuzzywuzzy dedupe example shape.
        val contains = listOf(
            "Frodo Baggin",
            "Frodo Baggins",
            "F. Baggins",
            "Samwise G.",
            "Gandalf",
            "Bilbo Baggins",
        )
        val result = dedupe(contains)
        // "Frodo Baggins" is the longest representative of the Frodo group.
        assertEquals(true, "Frodo Baggins" in result)
        assertEquals(false, "Frodo Baggin" in result)
        assertEquals(true, "Samwise G." in result)
        assertEquals(true, "Gandalf" in result)
        assertEquals(true, result.size < contains.size)
    }

    @Test
    fun noDuplicatesReturnsAllInOrder() {
        val items = listOf("alpha", "bravo", "charlie")
        assertEquals(items, dedupe(items, threshold = 95.0))
    }

    @Test
    fun representativeIsLongestThenLexicographic() {
        // All three are mutual duplicates at this threshold; "aaab" and
        // "aaac" tie on length -> lexicographically smaller "aaab" wins.
        val result = dedupe(listOf("aaa", "aaac", "aaab"), threshold = 70.0)
        assertEquals(listOf("aaab"), result)
    }

    @Test
    fun genericItemsKeepOriginal() {
        data class Person(val id: Int, val name: String)
        val people = listOf(
            Person(1, "Frodo Baggins"),
            Person(2, "F. Baggins"),
            Person(3, "Gandalf"),
        )
        val result = dedupe(people, { it.name })
        assertEquals(listOf(Person(1, "Frodo Baggins"), Person(3, "Gandalf")), result)
    }

    @Test
    fun processorAppliedToKeysOnly() {
        val result = dedupe(listOf("FRODO BAGGINS!!", "frodo baggins"), processor = ::defaultProcess)
        assertEquals(1, result.size)
        // Longest original key is the representative (15 vs 13 code points).
        assertEquals("FRODO BAGGINS!!", result[0])
    }

    @Test
    fun keysMatchingNothingSurviveInsteadOfCrashing() {
        // An empty key scores 0 against everything under TokenSetRatio --
        // including itself -- so it can never join a group. It must come
        // through as its own entry, not crash the scan (regression: this
        // used to throw IndexOutOfBoundsException).
        assertEquals(listOf("", "a b"), dedupe(listOf("", "a b")))
        assertEquals(listOf("!!!", "hello"), dedupe(listOf("!!!", "hello"), processor = ::defaultProcess))
        assertEquals(listOf(""), dedupe(listOf("", "")).distinct())
    }

    @Test
    fun tieBreakUsesCodePointOrder() {
        // Both keys are one code point + "x" and mutual duplicates under a
        // permissive threshold. U+1F600 (astral) sorts AFTER U+FF01 in code
        // points, though BEFORE it in raw UTF-16 comparison -- Python/
        // fuzzywuzzy semantics require the code-point order winner.
        val a = "😀x" // 😀x
        val b = "！x"       // ！x
        val result = dedupe(listOf(a, b), threshold = 0.0, scorer = Scorers.Ratio)
        assertEquals(listOf(b), result)
    }

    @Test
    fun singleAndEmptyInput() {
        assertEquals(emptyList(), dedupe(emptyList<String>()))
        assertEquals(listOf("x"), dedupe(listOf("x")))
    }
}
