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

    @Test
    fun realWorldQueries() {
        fun first(q: String) = engine.search(q).first().item.name
        assertTrue(first("pumkin spice").contains("Pumpkin Spice"), first("pumkin spice"))
        assertTrue(first("frap").contains("Frappuccino"), first("frap"))
        assertTrue(first("cold foam").endsWith("Cold Foam"), first("cold foam"))
        assertTrue(engine.search("frap").take(5).all { it.item.name.contains("Frappuccino") }, engine.search("frap").take(5).map { it.item.name }.toString())
        // A nickname never wins through a partial match inside an unrelated word.
        engine.search("plumbing").filter { it.item.name.contains("Pumpkin Spice Latte") }.forEach {
            assertEquals(it.nameScore, it.textScore, it.item.name)
        }
        assertTrue(first("psl").contains("Pumpkin Spice Latte"), first("psl"))
        assertTrue(first("caffe latte") == "Caffè Latte", first("caffe latte"))
        assertTrue(first("pike").contains("Pike Place"), first("pike"))
        assertTrue(first("capucino").contains("Cappuccino"), first("capucino"))
        assertTrue(first("americano") == "Caffè Americano", first("americano"))
        assertTrue(first("egg bites").contains("Egg Bites"), first("egg bites"))
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
