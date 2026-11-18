package io.github.likhithsj.menusearch

import io.github.likhithsj.menusearch.menu.MenuCatalog
import io.github.likhithsj.menusearch.menu.MenuRepository
import io.github.likhithsj.menusearch.menu.MenuSnapshot
import io.github.likhithsj.menusearch.search.MenuSearchEngine
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs against the REAL bundled snapshot (the file the app ships), so a
 * refresh via tools/fetch_menu.py that changes the payload shape, or a
 * ranking regression on real product names, fails here first.
 */
class BundledSnapshotTest {
    private val catalog: MenuCatalog by lazy {
        val file = File("src/commonMain/composeResources/files/menu.json")
        assertTrue(file.exists(), "snapshot missing at ${file.absolutePath}")
        MenuCatalog.from(MenuRepository.lenientJson.decodeFromString(MenuSnapshot.serializer(), file.readText()))
    }
    private val engine by lazy { MenuSearchEngine(catalog) }

    @Test
    fun snapshotParsesIntoAFullMenu() {
        assertTrue(catalog.items.size > 200, "only ${catalog.items.size} items")
        assertTrue(catalog.tree.size >= 5)
        assertTrue(catalog.items.all { it.name.isNotBlank() })
        assertTrue(catalog.items.count { it.imageUrl != null } > catalog.items.size / 2)
    }

    // Evergreen items only: the snapshot is refreshed with tools/fetch_menu.py
    // and seasonal drinks (pumpkin, peppermint, ...) come and go with it.
    @Test
    fun realWorldQueries() {
        fun first(q: String) = engine.search(q).first().item.name
        assertTrue(first("capucino").contains("Cappuccino"), first("capucino"))
        assertEquals("Caffè Americano", first("americano"))
        assertEquals("Caffè Latte", first("caffe latte"))
        assertTrue(first("cold foam").endsWith("Cold Foam"), first("cold foam"))
        assertTrue(first("egg bites").contains("Egg Bites"), first("egg bites"))
        assertTrue(first("pike").contains("Pike Place"), first("pike"))
        val frap = engine.search("frap").take(5).map { it.item.name }
        assertTrue(frap.all { it.contains("Frappuccino") }, frap.toString())
    }

    @Test
    fun scopedSearchIsNotStarvedByTheRestOfTheMenu() {
        // "latte" matches far more than 5 items menu-wide; scoping to the food
        // tree must still return food, not an empty tail of the global top 5.
        val food = catalog.tree.single { it.name == "Food" }.allItems().mapTo(HashSet()) { it.id }
        val scoped = engine.search("egg", limit = 5, within = food)
        assertTrue(scoped.isNotEmpty())
        assertTrue(scoped.all { it.item.id in food })
        val drinksOnly = engine.search("latte", limit = 5, within = food)
        assertTrue(drinksOnly.all { it.item.id in food })
    }

    @Test
    fun noDuplicateRowsInResults() {
        val ids = engine.search("latte", limit = 40).map { it.item.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyResultClearsTheCutoff() {
        for (q in listOf("mocha", "tea", "bagel", "cold brew")) {
            val hits = engine.search(q)
            assertTrue(hits.isNotEmpty(), q)
            assertTrue(hits.all { it.textScore >= MenuSearchEngine.SCORE_CUTOFF }, q)
        }
    }
}
