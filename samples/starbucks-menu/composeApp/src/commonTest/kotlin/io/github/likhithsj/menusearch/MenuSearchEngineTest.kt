package io.github.likhithsj.menusearch

import io.github.likhithsj.menusearch.menu.MenuCatalog
import io.github.likhithsj.menusearch.menu.MenuRepository
import io.github.likhithsj.menusearch.menu.MenuResponse
import io.github.likhithsj.kmatch.Fuzz
import io.github.likhithsj.menusearch.search.MenuSearchEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MenuSearchEngineTest {
    private val catalog = MenuCatalog.from(
        MenuRepository.lenientJson.decodeFromString(MenuResponse.serializer(), FIXTURE_JSON),
        fetchedAt = null, sourceUrl = null,
    )
    private val engine = MenuSearchEngine(catalog)

    private fun top(query: String) = engine.search(query).first().item.name

    @Test
    fun typoStillFindsTheDrink() {
        assertEquals("Cappuccino", top("capuchino"))
        assertEquals("Pumpkin Spice Latte", top("pumkin spice"))
    }

    @Test
    fun accentsAreOptional() {
        assertEquals("Caffè Latte", top("caffe latte"))
        assertEquals("Jalapeño Cheddar Bagel", top("jalapeno bagel"))
    }

    @Test
    fun nicknamesComeFromTheAliasTable() {
        val names = engine.search("frap").map { it.item.name }
        assertTrue(names.isNotEmpty(), "alias 'frap' should surface Frappuccinos")
        assertTrue(names.all { it.contains("Frappuccino") }, "got $names")
        assertEquals("Pumpkin Spice Latte", top("psl"))
    }

    @Test
    fun registeredTrademarkSymbolDoesNotHurtMatching() {
        assertEquals("Caffè Verona®", top("verona"))
    }

    @Test
    fun featuredItemsWinTiesButNotBetterMatches() {
        // "mocha": the featured Caffè Mocha (boost 1.0) and White Chocolate Mocha
        // both contain the word; the featured one must rank first.
        assertEquals("Caffè Mocha", top("mocha"))
        // "white chocolate" is a clearly better text match for the non-featured drink.
        assertEquals("White Chocolate Mocha", top("white chocolate"))
    }

    @Test
    fun rankScoreIsTextScoreTimesBoost() {
        val hit = engine.search("americano").first()
        assertEquals("Caffè Americano", hit.item.name)
        assertEquals(hit.textScore * MenuSearchEngine.defaultBoost(hit.item), hit.rankScore)
        assertTrue(hit.textScore >= MenuSearchEngine.SCORE_CUTOFF)
    }

    @Test
    fun shortOrBlankQueriesReturnNothing() {
        assertTrue(engine.search("").isEmpty())
        assertTrue(engine.search(" c ").isEmpty())
    }

    @Test
    fun nonsenseReturnsNothingAboveTheCutoff() {
        assertTrue(engine.search("zzqxv plumbing").isEmpty())
    }

    @Test
    fun highlightsPointIntoTheDisplayedName() {
        val hit = engine.search("cheddar").first()
        assertEquals("Jalapeño Cheddar Bagel", hit.item.name)
        val text = hit.highlights.joinToString("") { hit.item.name.substring(it.first, it.last + 1) }
        assertEquals("cheddar", text.lowercase())
    }

    @Test
    fun searchKeysAreShortAndSeparate() {
        fun keys(name: String) = MenuSearchEngine.searchKeys(catalog.items.single { it.name.startsWith(name) }).map { it.text }
        assertEquals(listOf("Cappuccino"), keys("Cappuccino"))
        assertEquals(listOf("Caffè Verona®", "Caffè Verona® Dark Roast"), keys("Caffè Verona"))
        assertEquals(
            listOf("Mocha Frappuccino® Blended Beverage", "Mocha Frappuccino® Blended Beverage Coffee Frappuccino®", "frap", "frappe"),
            keys("Mocha Frappuccino"),
        )
        // Featured-only placement never contributes a key: "Trending" is a shelf.
        val psl = catalog.items.single { it.name == "Pumpkin Spice Latte" }
        assertEquals(listOf(MenuSearchEngine.KeyKind.NAME, MenuSearchEngine.KeyKind.ALIAS), MenuSearchEngine.searchKeys(psl).map { it.kind })
    }

    @Test
    fun nicknameScoresAtFullStrengthDespiteLongProductNames() {
        val hit = engine.search("frap").first()
        assertTrue(hit.item.name.contains("Frappuccino"), hit.item.name)
        assertEquals(100.0, hit.textScore, "the alias key 'frap' is an exact token match")
        assertTrue(hit.nameScore < hit.textScore, "the long name alone scored ${hit.nameScore}")
    }

    @Test
    fun nicknamesNeedWholeTokens() {
        // "psl" partially matches inside "plumbing" under WRatio (60, above the
        // cutoff) but not under the whole-token scorer the alias scan uses.
        assertTrue(Fuzz.weightedRatio("plumbing", "psl") >= MenuSearchEngine.SCORE_CUTOFF)
        assertTrue(Fuzz.tokenSetRatio("plumbing", "psl") < MenuSearchEngine.SCORE_CUTOFF)
        // So if PSL shows up for "plumbing" at all, it is its NAME that matched
        // ("pumpkin" ~ "plumbing" by indel), never the nickname.
        engine.search("plumbing").filter { it.item.name == "Pumpkin Spice Latte" }.forEach {
            assertEquals(it.nameScore, it.textScore, "alias key must not be the winning key")
        }
        // ...while a typo'd nickname still works.
        assertEquals("Pumpkin Spice Latte", top("psll"))
    }

    @Test
    fun categoryWordIsAWeakerSignalThanTheName() {
        // "Caffè Americano" matches by name; nothing else in the fixture does.
        // Put a category-only competitor in and the name must still win at
        // equal raw score: that is what KeyKind.CATEGORY's weight is for.
        assertTrue(MenuSearchEngine.KeyKind.CATEGORY.weight < MenuSearchEngine.KeyKind.NAME.weight)
        val hit = engine.search("dark roast").first()
        assertEquals("Caffè Verona®", hit.item.name)
        assertEquals(0.0, hit.nameScore)
        assertEquals(hit.textScore * MenuSearchEngine.KeyKind.CATEGORY.weight * MenuSearchEngine.defaultBoost(hit.item), hit.rankScore)
    }

    @Test
    fun nameMatchOutranksCategoryMatchOnEqualScore() {
        // Both "Caffè Latte" and the featured PSL contain "latte"; PSL is boosted
        // so it wins. But among NON-featured items a bare-name hit must beat one
        // that only matched through its category word.
        val hits = engine.search("latte")
        val latteIdx = hits.indexOfFirst { it.item.name == "Caffè Latte" }
        assertTrue(latteIdx >= 0)
        assertTrue(hits.first().item.name == "Pumpkin Spice Latte", hits.map { it.item.name }.toString())
    }
}
