import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The migration evidence, pinned as tests. Only deterministic properties are
 * asserted; score-level differences between the two engines belong in the
 * printed demo table, not in assertions.
 */
class SideBySideTest {

    // --- The identity bug (the reason to migrate at all) --------------------

    @Test
    fun legacyCanNeverReturnTheSecondSameNamedForm() {
        // Both "Caffè Latte" forms match the query, so two results come back
        // -- but the name lookup maps BOTH to the first form. The ICED form
        // (2122) is unreachable through legacy search, forever.
        val hits = LegacyProductSearch.search("Caffè Latte", catalog)
        val latteHits = hits.filter { it.name == "Caffè Latte" }
        assertTrue(latteHits.size >= 2, "both duplicate names should match")
        assertTrue(latteHits.all { it.productNumber == "2121" })
        assertTrue(latteHits.none { it.productNumber == "2122" })
    }

    @Test
    fun kmatchReturnsEachFormAsItself() {
        val hits = KmatchProductSearch.search("Caffè Latte", catalog)
            .filter { it.name == "Caffè Latte" }
        assertEquals(
            setOf("2121", "2122"),
            hits.map { it.productNumber }.toSet(),
            "both forms must be reachable, each as its own object",
        )
    }

    // --- Accent folding (what the processor hook buys) -----------------------

    @Test
    fun unaccentedQueryFindsAccentedProduct() {
        val top = KmatchProductSearch.searchScored("jalapeno cheddar bagel", catalog).first()
        assertEquals("873", top.item.productNumber)
        assertEquals(100.0, top.score, "folded, this is an exact match")
    }

    @Test
    fun accentedAndUnaccentedQueriesScoreTheSame() {
        val accented = KmatchProductSearch.searchScored("jalapeño", catalog).first()
        val plain = KmatchProductSearch.searchScored("jalapeno", catalog).first()
        assertEquals(accented.item, plain.item)
        assertEquals(accented.score, plain.score)
    }

    // --- The contract a drop-in replacement must keep ------------------------

    @Test
    fun sameSignatureSameHappyPath() {
        // For an everyday query both engines agree on the winner; the swap
        // changes wrong answers, not right ones.
        val q = "cold brew"
        assertEquals("601", LegacyProductSearch.search(q, catalog).first().productNumber)
        assertEquals("601", KmatchProductSearch.search(q, catalog).first().productNumber)
    }

    @Test
    fun scoresSurviveForRanking() {
        // searchScored keeps the Double score -- the hook for category
        // boosts (see samples/menu-search) that legacy search cannot offer.
        val scored = KmatchProductSearch.searchScored("latte", catalog)
        assertTrue(scored.isNotEmpty())
        assertTrue(scored.zipWithNext().all { (a, b) -> a.score >= b.score })
    }
}
