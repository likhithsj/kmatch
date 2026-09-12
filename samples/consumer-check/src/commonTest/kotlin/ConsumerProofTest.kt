import io.github.likhithsj.kmatch.Fuzz
import io.github.likhithsj.kmatch.dedupe
import io.github.likhithsj.kmatch.defaultProcess
import io.github.likhithsj.kmatch.extractOne
import io.github.likhithsj.kmatch.matchingRanges
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden-value assertions run by a CONSUMER of the published Maven Central
 * artifact, on every environment the CI matrix can execute. Each expected
 * value is the documented RapidFuzz-identical result; a pass on a platform
 * means that platform's artifact resolves, links, and scores bit-exactly.
 */
class ConsumerProofTest {

    @Test
    fun ratioIsBitExact() {
        assertEquals(96.55172413793103, Fuzz.ratio("this is a test", "this is a test!"))
    }

    @Test
    fun partialRatioFindsTheWindow() {
        assertEquals(100.0, Fuzz.partialRatio("this is a test", "this is a test!"))
    }

    @Test
    fun tokenSortIgnoresWordOrder() {
        assertEquals(100.0, Fuzz.tokenSortRatio("fuzzy wuzzy was a bear", "wuzzy fuzzy was a bear"))
    }

    @Test
    fun weightedRatioAndCutoffContract() {
        assertEquals(95.0, Fuzz.weightedRatio("hello world", "hello world xy"))
        // Below-cutoff results return 0.0 -- including through internal
        // early-exit paths (the class of bug the cutoff vectors fence).
        assertEquals(0.0, Fuzz.weightedRatio("hello world", "hello world xy", scoreCutoff = 96.0))
    }

    @Test
    fun unicodeIsCodePointCorrect() {
        // a != ã, and the accented pair still scores identically to Python.
        assertEquals(66.66666666666667, Fuzz.ratio("sao", "são"))
    }

    @Test
    fun extractionFindsBestMatch() {
        val choices = listOf("Atlanta Falcons", "New York Jets", "New York Giants", "Dallas Cowboys")
        val best = extractOne("new york jets", choices, processor = ::defaultProcess)!!
        assertEquals("New York Jets", best.choice)
        assertEquals(100.0, best.score)
        assertEquals(1, best.index)
    }

    @Test
    fun genericExtractionReturnsRecords() {
        data class Product(val name: String, val id: Int)
        val products = listOf(Product("Caffe Latte", 1), Product("Flat White", 2), Product("Cold Brew", 3))
        val hit = extractOne("caffe latte", products, keySelector = { it.name }, processor = ::defaultProcess)!!
        assertEquals(1, hit.item.id)
        assertEquals(100.0, hit.score)
    }

    @Test
    fun dedupeCollapsesNearDuplicates() {
        val result = dedupe(listOf("Frodo Baggins", "Frodo Baggin", "F. Baggins", "Gandalf"))
        assertTrue("Frodo Baggins" in result)
        assertTrue("Gandalf" in result)
        assertTrue(result.size < 4)
    }

    @Test
    fun highlightingCoversTheMatch() {
        val text = "the new york times"
        val highlighted = matchingRanges("new york", text)
            .joinToString("") { text.substring(it.first, it.last + 1) }
        assertEquals("new york", highlighted)
    }
}
