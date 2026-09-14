import ca.solostudios.fuzzykt.FuzzyKt

// ---------------------------------------------------------------------------
// The comparison table: one realistic menu, a set of realistic (mistyped,
// keyword-only, accented) queries, three engines side by side:
//
//   NAIVE   -- substring contains() on lowercased names (what search often
//              starts as)
//   KTFUZZY -- best ratio() over names using an existing Kotlin fuzzy library
//              (manual loop; no keywords, no ranking API)
//   KMATCH  -- MenuSearch above (extractTop + keySelector + fold processor)
// ---------------------------------------------------------------------------

val menu = MenuCategory(
    "root",
    children = listOf(
        MenuCategory(
            "Hot Coffee",
            items = listOf(
                // Keyword-map design note: keywords are ALIASES users type,
                // not ingredient lists. Putting "espresso" in every
                // espresso-based drink would make them all compete with the
                // actual Espresso product for that query.
                MenuItem("1", "Caffè Latte", listOf("latte", "milk")),
                MenuItem("2", "Caffè Americano", listOf("americano", "long black")),
                MenuItem("3", "Cappuccino", listOf("foam", "cappucino")),
                MenuItem("4", "Caramel Macchiato", listOf("caramel", "vanilla")),
                MenuItem("5", "Espresso", listOf("shot", "doppio")),
            ),
        ),
        MenuCategory(
            "Cold Drinks",
            items = listOf(
                MenuItem("6", "Iced Matcha Tea Latte", listOf("matcha", "green tea", "iced")),
                MenuItem("7", "Cold Brew", listOf("cold brew", "slow steeped")),
                MenuItem("8", "Mango Dragonfruit Refresher", listOf("mango", "dragonfruit", "refresher")),
            ),
        ),
        MenuCategory(
            "Food",
            items = listOf(
                MenuItem("9", "Jalapeño Cheddar Bagel", listOf("bagel", "spicy", "cheese")),
                MenuItem("10", "Chocolate Croissant", listOf("pastry", "chocolate")),
            ),
        ),
    ),
)

fun naiveSearch(query: String, items: List<MenuItem>): String =
    items.firstOrNull { query.lowercase() in it.name.lowercase() }?.name ?: "—"

fun ktFuzzySearch(query: String, items: List<MenuItem>): String =
    items.maxByOrNull { FuzzyKt.ratio(query.lowercase(), it.name.lowercase()) }?.name ?: "—"

fun main() {
    val engine = MenuSearch(menu)
    val items = menu.allItems()
    val queries = listOf(
        "latte",            // plain keyword
        "carmel machiato",  // two typos
        "expresso",         // classic misspelling
        "jalapeno bagel",   // no accent typed
        "matcha late",      // typo + partial
        "dragon fruit",     // spacing differs
        "doppio",           // keyword only -- not in any display name
    )

    println("%-18s | %-28s | %-28s | %s".format("QUERY", "NAIVE contains()", "KT-FUZZY best ratio()", "KMATCH extractTop()"))
    println("-".repeat(110))
    for (q in queries) {
        val km = engine.search(q, limit = 1).firstOrNull()
        val kmatchCell = km?.let { "%s  (%.0f)".format(it.item.name, it.score) } ?: "—"
        println("%-18s | %-28s | %-28s | %s".format(q, naiveSearch(q, items), ktFuzzySearch(q, items), kmatchCell))
    }
    println()
    println("kmatch entry point: extractTop(query, items, keySelector = { name + keywords }, processor = ::searchFold, scoreCutoff = 55.0)")
}
