import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The holiday-boost example from docs/learn/07-categories-and-priorities.md,
 * pinned as tests: kmatch scores text only; one sort applies the business
 * priority, and this is exactly what it changes.
 */
class HolidayBoostTest {

    private val engine = MenuSearch(menu)

    @Test
    fun rawSearchIsPurelyTextual() {
        // "mocha" matches Caffè Mocha and Peppermint Mocha equally well on
        // TEXT, and raw results keep input order on a tie -- the regular
        // drink comes first because it appears earlier in the menu.
        val raw = engine.search("mocha", limit = 2)
        assertEquals("Caffè Mocha", raw[0].item.name)
        assertEquals("Peppermint Mocha", raw[1].item.name)
        assertEquals(raw[0].score, raw[1].score) // genuine tie, text-wise
    }

    @Test
    fun boostPromotesHolidayOnTies() {
        // Same query, one multiply-and-sort on top: the holiday drink wins
        // the tie (1.0 vs 0.95) without any change to the library call.
        val boosted = boostedSearch(engine, "mocha")
        assertEquals("Peppermint Mocha", boosted[0].first.item.name)
        assertEquals("Caffè Mocha", boosted[1].first.item.name)
    }

    @Test
    fun boostNeverForcesIrrelevantItems() {
        // Relevance still rules: for "cold brew" no holiday item is close,
        // and the boost cannot invent one into the results.
        val boosted = boostedSearch(engine, "cold brew")
        assertEquals("Cold Brew", boosted[0].first.item.name)
    }
}
