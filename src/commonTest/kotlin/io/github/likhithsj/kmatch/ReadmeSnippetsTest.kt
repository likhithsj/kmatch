package io.github.likhithsj.kmatch

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every snippet in README.md that states a result, written exactly as the
 * README writes it. If one of these stops compiling or changes its answer,
 * the README is wrong: fix both together.
 */
class ReadmeSnippetsTest {

    private data class City(val name: String, val population: Int)
    private data class Product(val name: String, val keywords: List<String>)

    @Test
    fun scorers() {
        assertEquals(96.55172413793103, Fuzz.ratio("this is a test", "this is a test!"))
        assertEquals(100.0, Fuzz.partialRatio("this is a test", "this is a test!"))
        assertEquals(100.0, Fuzz.tokenSortRatio("fuzzy wuzzy was a bear", "wuzzy fuzzy was a bear"))
        assertEquals(100.0, Fuzz.tokenSetRatio("fuzzy was a bear but not a dog", "fuzzy was a bear"))
        assertEquals(100.0, Fuzz.ratio("Test String!", "test string", processor = ::defaultProcess))
        assertEquals(0.0, Fuzz.ratio("hello world", "hello world!", scoreCutoff = 99.0))
        assertEquals(50.0, Fuzz.ratio("myself", "me"))
    }

    @Test
    fun cutoffIsInclusive() {
        val score = Fuzz.ratio("hello world", "hello world!")
        assertEquals(score, Fuzz.ratio("hello world", "hello world!", scoreCutoff = score))
    }

    @Test
    fun extraction() {
        val choices = listOf("Atlanta Falcons", "New York Jets", "New York Giants", "Dallas Cowboys")
        assertEquals(
            ExtractedResult(choice = "New York Jets", score = 100.0, index = 1),
            extractOne("new york jets", choices, processor = ::defaultProcess),
        )
        assertEquals(2, extractTop("new york", choices, limit = 2, processor = ::defaultProcess).size)
        assertEquals(3, extractTop("new york", choices, scorer = Scorers.TokenSetRatio, limit = 3).size)
        // Defaults: WeightedRatio, limit 5.
        assertEquals(extractTop("new york", choices, scorer = Scorers.WeightedRatio, limit = 5), extractTop("new york", choices))

        val exact = Scorer { a, b, _ -> if (a == b) 100.0 else 0.0 }
        assertEquals(0.0, extractOne("query", choices, scorer = exact)?.score)

        val cities = listOf(City("New York", 8300000), City("Newark", 300000))
        assertEquals(
            ExtractedItem(item = City(name = "New York", population = 8300000), score = 100.0, index = 0),
            extractOne("new york", cities, keySelector = { it.name }, processor = ::defaultProcess),
        )
    }

    @Test
    fun multiFieldExtraction() {
        val products = listOf(
            Product("Caffè Latte", listOf("latte", "milk")),
            Product("Espresso", listOf("shot", "doppio")),
        )
        // Written with implicit `it`, as in the README.
        val top = extractTop("doppio", products,
            keySelectors = listOf({ it.name }, { it.keywords.joinToString(" ") }),
            processor = ::searchFold)
        assertEquals("Espresso", top.first().item.name)
        assertEquals(90.0, top.first().score)
    }

    @Test
    fun dedupeAndHighlighting() {
        assertEquals(
            listOf("Frodo Baggins", "Gandalf"),
            dedupe(listOf("Frodo Baggins", "Frodo Baggin", "F. Baggins", "Gandalf")),
        )
        val products = listOf(Product("Cold Brew", emptyList()), Product("Brew, Cold", emptyList()), Product("Espresso", emptyList()))
        assertEquals(2, dedupe(products, keySelector = { it.name }).size)

        val choice = "the new york times"
        assertEquals("new york", matchingRanges("new york", choice).joinToString("") { choice.substring(it.first, it.last + 1) })
        val blocks: List<MatchingBlock> = matchingBlocks("kitten", "sitting")
        assertEquals(4, blocks.sumOf { it.length }) // "ittn": the longest common subsequence
    }

    @Test
    fun accentInsensitiveSearch() {
        assertEquals(100.0, Fuzz.ratio("caffe latte", "Caffè Latte", processor = ::searchFold))
        assertEquals("Lodz", stripAccents("Łódź"))
        assertEquals("caffe", searchFold("  Caffè! "))
    }

    @Test
    fun parityNotes() {
        // "How parity is verified" and the Roadmap's 1.0.1 entry.
        assertEquals(83.33333333333334, Fuzz.partialRatio("abbabbaabba", "bababa"))
        assertEquals(83.33333333333334, Fuzz.partialRatio("abbabbaabba", "bababa", scoreCutoff = 83.33333333333334))
        assertEquals(100.0, Fuzz.tokenSortRatio("Łódź Kaliska", "Kaliska Łódź"))
        assertEquals(66.66666666666667, Fuzz.tokenSortRatio("Zürich HB", "HB Zürich"))
    }
}
