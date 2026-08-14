package io.github.likhithsj.kmatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtractTest {

    private val choices = listOf("Atlanta Falcons", "New York Jets", "New York Giants", "Dallas Cowboys")

    @Test
    fun extractOneFindsBestMatch() {
        val result = extractOne("new york jets", choices, processor = ::defaultProcess)
        assertEquals("New York Jets", result?.choice)
        assertEquals(1, result?.index)
        assertEquals(100.0, result?.score)
    }

    @Test
    fun extractOneFirstOfTiedScoresWins() {
        val result = extractOne("a", listOf("ab", "ba", "ab"))
        assertEquals(0, result?.index)
    }

    @Test
    fun extractOneRespectsCutoff() {
        assertNull(extractOne("zzzz", choices, scoreCutoff = 60.0))
    }

    @Test
    fun extractTopSortsByScoreWithStableTies() {
        val results = extractTop("new york", choices, limit = 2, processor = ::defaultProcess)
        assertEquals(2, results.size)
        assertEquals(listOf("New York Jets", "New York Giants"), results.map { it.choice })
        assertTrue(results[0].score >= results[1].score)
    }

    @Test
    fun extractAllKeepsInputOrderAndFilters() {
        val results = extractAll("new york", choices, processor = ::defaultProcess, scoreCutoff = 50.0)
        assertEquals(listOf(1, 2), results.map { it.index })
    }

    @Test
    fun extractSortedReturnsEverythingSorted() {
        val results = extractSorted("new york", choices, processor = ::defaultProcess)
        assertEquals(choices.size, results.size)
        assertTrue(results.zipWithNext().all { (a, b) -> a.score >= b.score })
    }

    @Test
    fun customScorerViaFunInterface() {
        val exact = Scorer { a, b, _ -> if (a == b) 100.0 else 0.0 }
        val result = extractOne("New York Jets", choices, scorer = exact)
        assertEquals("New York Jets", result?.choice)
    }

    @Test
    fun originalChoiceIsReturnedWhenProcessorUsed() {
        val result = extractOne("DALLAS COWBOYS!!!", choices, processor = ::defaultProcess)
        assertEquals("Dallas Cowboys", result?.choice)
    }
}
