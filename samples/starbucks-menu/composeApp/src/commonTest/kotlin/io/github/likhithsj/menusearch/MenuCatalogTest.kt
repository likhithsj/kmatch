package io.github.likhithsj.menusearch

import io.github.likhithsj.menusearch.menu.MenuCatalog
import io.github.likhithsj.menusearch.menu.MenuRepository
import io.github.likhithsj.menusearch.menu.MenuResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MenuCatalogTest {
    private val catalog = MenuCatalog.from(
        MenuRepository.lenientJson.decodeFromString(MenuResponse.serializer(), FIXTURE_JSON),
        fetchedAt = null, sourceUrl = null,
    )

    @Test
    fun duplicatesAcrossCategoriesCollapseIntoOneItemThatRemembersBothPaths() {
        val psl = catalog.items.single { it.name == "Pumpkin Spice Latte" }
        assertEquals(2, psl.categories.size)
        assertTrue(psl.isFeatured)
        // The real home category is primary even though "The Latest" was parsed first.
        assertEquals("Latte", psl.primaryCategory.leaf)
        assertEquals("Latte › Hot Coffee & Espresso › Coffee & Espresso", psl.primaryCategory.display)
    }

    @Test
    fun itemCountIsDistinctProducts() {
        // 13 product entries in the fixture, 2 of them repeats of featured drinks.
        assertEquals(11, catalog.items.size)
    }

    @Test
    fun treeNodesShareTheMergedItemInstances() {
        val trending = catalog.tree.first().children.single()
        val fromTree = trending.items.single { it.name == "Pumpkin Spice Latte" }
        assertEquals(2, fromTree.categories.size)
        assertEquals("https://www.starbucks.com/menu/product/100/hot", fromTree.webUrl)
    }

    @Test
    fun blankFormCodeBecomesNull() {
        val bagel = catalog.items.single { it.name.startsWith("Jalape") }
        assertEquals(null, bagel.form)
        assertNotNull(catalog.tree.single { it.name == "Food" }.walk().firstOrNull { it.name == "Bagels" })
    }

    @Test
    fun unknownFieldsAndMissingFieldsAreTolerated() {
        val json = """{"menus":[{"name":"X","products":[{"name":"Thing","productNumber":1,"newField":{"a":1}}],"children":[],"surprise":true}]}"""
        val cat = MenuCatalog.from(MenuRepository.lenientJson.decodeFromString(MenuResponse.serializer(), json), null, null)
        assertEquals("Thing", cat.items.single().name)
    }

    private fun io.github.likhithsj.menusearch.menu.BrowseNode.walk(): Sequence<io.github.likhithsj.menusearch.menu.BrowseNode> =
        sequence { yield(this@walk); children.forEach { yieldAll(it.walk()) } }
}
