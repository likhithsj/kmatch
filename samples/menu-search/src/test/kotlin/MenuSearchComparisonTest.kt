import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "old approach vs kmatch" test the integration discussion asked for:
 * each case is a query a real user types; the assertions document what the
 * naive approach misses and what the kmatch-backed MenuSearch returns.
 */
class MenuSearchComparisonTest {

    private val engine = MenuSearch(menu)
    private val items = menu.allItems()

    @Test
    fun typosBeatSubstringSearch() {
        // Naive contains() finds nothing for a typo; kmatch ranks the right
        // item first.
        assertEquals("—", naiveSearch("carmel machiato", items))
        assertEquals("Caramel Macchiato", engine.search("carmel machiato").first().item.name)

        assertEquals("—", naiveSearch("expresso", items))
        assertEquals("Espresso", engine.search("expresso").first().item.name)
    }

    @Test
    fun accentFoldingFindsAccentedItems() {
        // Nobody types the ñ. The searchFold processor folds both sides.
        assertEquals("Jalapeño Cheddar Bagel", engine.search("jalapeno bagel").first().item.name)
    }

    @Test
    fun keywordsMapParticipatesInMatching() {
        // "doppio" appears only in the keywords map, not in any display name
        // -- substring over names can't find it at all.
        assertEquals("—", naiveSearch("doppio", items))
        assertEquals("Espresso", engine.search("doppio").first().item.name)
    }

    @Test
    fun partialAndMistypedQueriesRankSensibly() {
        assertEquals("Iced Matcha Tea Latte", engine.search("matcha late").first().item.name)
        assertEquals("Mango Dragonfruit Refresher", engine.search("dragon fruit").first().item.name)
    }

    @Test
    fun cutoffKeepsGarbageOut() {
        // A nonsense query returns nothing rather than a confident wrong answer.
        assertTrue(engine.search("wxqzt plgh").isEmpty())
    }

    @Test
    fun resultsCarryTheDomainObjectAndScore() {
        val hit = engine.search("latte").first()
        assertEquals("1", hit.item.id)            // your MenuItem, not a string
        assertTrue(hit.score >= 90.0)             // and the ranking signal
    }
}
