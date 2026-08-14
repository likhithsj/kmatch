package io.github.likhithsj.kmatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GenericExtractTest {

    private data class City(val name: String, val population: Int)

    private val cities = listOf(
        City("New York", 8_300_000),
        City("Newark", 310_000),
        City("York", 210_000),
        City("San Francisco", 800_000),
    )

    @Test
    fun extractOneOverRecords() {
        val best = extractOne("new york", cities, { it.name }, processor = ::defaultProcess)!!
        assertEquals(cities[0], best.item)
        assertEquals(100.0, best.score)
        assertEquals(0, best.index)
    }

    @Test
    fun genericAgreesWithStringSurface() {
        val names = cities.map { it.name }
        for (scorer in listOf(Scorers.WeightedRatio, Scorers.Ratio, Scorers.PartialRatio)) {
            for (cutoff in listOf(null, 50.0)) {
                val viaString = extractAll("new york", names, scorer, null, cutoff)
                val viaGeneric = extractAll("new york", cities, { it.name }, scorer, null, cutoff)
                assertEquals(viaString.map { it.choice }, viaGeneric.map { it.item.name })
                assertEquals(viaString.map { it.score }, viaGeneric.map { it.score })
                assertEquals(viaString.map { it.index }, viaGeneric.map { it.index })

                val sortedString = extractSorted("new york", names, scorer, null, cutoff)
                val sortedGeneric = extractSorted("new york", cities, { it.name }, scorer, null, cutoff)
                assertEquals(sortedString.map { it.choice }, sortedGeneric.map { it.item.name })

                val topString = extractTop("new york", names, 2, scorer, null, cutoff)
                val topGeneric = extractTop("new york", cities, { it.name }, 2, scorer, null, cutoff)
                assertEquals(topString.map { it.choice to it.score }, topGeneric.map { it.item.name to it.score })
            }
        }
    }

    @Test
    fun cutoffFiltersRecords() {
        assertNull(extractOne("zzzzz", cities, { it.name }, scoreCutoff = 90.0))
        val all = extractAll("new york", cities, { it.name }, scoreCutoff = 60.0)
        assertEquals(true, all.all { it.score >= 60.0 })
    }

    @Test
    fun tieKeepsFirstItem() {
        val items = listOf("b" to 1, "ab" to 2, "ab" to 3)
        val best = extractOne("ab", items, { it.first })!!
        assertEquals(2, best.item.second)
        assertEquals(1, best.index)
    }
}
