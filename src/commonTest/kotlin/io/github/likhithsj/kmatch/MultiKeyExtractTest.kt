package io.github.likhithsj.kmatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultiKeyExtractTest {

    private data class Product(val id: String, val name: String, val keywords: String)

    private val products = listOf(
        Product("1", "Caffè Latte", "latte milk"),
        Product("2", "Peppermint Mocha", "mocha mint peppermint candy cane holiday christmas seasonal winter"),
        Product("3", "Cold Brew", "cold brew slow steeped"),
        Product("4", "Espresso", "shot doppio"),
    )

    private val keys = listOf<(Product) -> String>({ it.name }, { it.keywords })

    @Test
    fun bestFieldWins() {
        // "doppio" appears only in keywords: the item's score IS the keyword
        // field's score (weightedRatio's partial path: 100 x 0.9), with the
        // non-matching name unable to dilute it.
        val hit = extractOne("doppio", products, keys, processor = ::defaultProcess)
        assertEquals("4", hit?.item?.id)
        assertEquals(90.0, hit?.score)
    }

    @Test
    fun perFieldScoringBeatsConcatenationOnLongAliasLists() {
        // The length-ratio cliff, pinned: product 2 carries a long keyword
        // list. Concatenating name+keywords pushes the key past 8x the query
        // length and weightedRatio down-scales the match; scoring the name
        // field on its own keeps the true 90.
        val multi = extractSorted("mocha", products, keys, processor = ::defaultProcess)
            .first { it.item.id == "2" }
        val concat = extractSorted(
            "mocha", products,
            keySelector = { "${it.name} ${it.keywords}" },
            processor = ::defaultProcess,
        ).first { it.item.id == "2" }
        assertEquals(90.0, multi.score)
        assertTrue(concat.score < multi.score, "concatenated=${concat.score} should sit below multi=${multi.score}")
    }

    @Test
    fun cutoffAppliesToTheBestField() {
        assertNull(extractOne("mocha", products, keys, processor = ::defaultProcess, scoreCutoff = 95.0))
        val all = extractAll("mocha", products, keys, processor = ::defaultProcess, scoreCutoff = 85.0)
        assertEquals(listOf("2"), all.map { it.item.id })
    }

    @Test
    fun sortedTiesKeepInputOrder() {
        val data = listOf(Product("a", "same", ""), Product("b", "same", ""))
        val hits = extractSorted("same", data, listOf({ p: Product -> p.name }, { p: Product -> p.keywords }))
        assertEquals(listOf("a", "b"), hits.map { it.item.id })
        assertEquals(hits[0].score, hits[1].score)
    }

    @Test
    fun agreesWithSingleKeyWhenOneSelectorGiven() {
        for (q in listOf("latte", "cold brw", "espresso")) {
            val single = extractSorted(q, products, keySelector = { it.name }, processor = ::defaultProcess)
            val multi = extractSorted(q, products, listOf<(Product) -> String>({ it.name }), processor = ::defaultProcess)
            assertEquals(single, multi)
        }
    }

    @Test
    fun topLimitsAfterRanking() {
        val top = extractTop("mocha latte", products, keys, limit = 2, processor = ::defaultProcess)
        assertEquals(2, top.size)
        assertTrue(top[0].score >= top[1].score)
    }

    @Test
    fun emptySelectorListIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            extractAll("x", products, emptyList<(Product) -> String>())
        }
        assertFailsWith<IllegalArgumentException> {
            extractOne("x", products, emptyList<(Product) -> String>())
        }
    }

    @Test
    fun worksWithProcessorAndFold() {
        val hit = extractOne("caffe latte", products, keys, processor = ::searchFold)
        assertEquals("1", hit?.item?.id)
        assertEquals(100.0, hit?.score)
    }
}
