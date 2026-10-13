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
                MenuItem("11", "Caffè Mocha", listOf("mocha", "chocolate")),
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
        // Seasonal category: last in the tree on purpose, so on a raw-score
        // TIE the regular item (earlier input order) wins -- which makes the
        // effect of the boost below unmistakable.
        //
        // Keyword-design note #2 (discovered by this very demo): keep
        // keyword lists SHORT. A long list once pushed "Peppermint Mocha"'s
        // searchable text past 8x the length of the query "mocha", tripping
        // WRatio's length-ratio cliff (scale 0.9 -> 0.6) and dropping its
        // score from 90 to 60.
        MenuCategory(
            "Holiday Drinks",
            items = listOf(
                MenuItem("12", "Peppermint Mocha", listOf("mocha", "mint")),
                MenuItem("13", "Gingerbread Latte", listOf("gingerbread", "holiday")),
            ),
        ),
    ),
)

// --- Holiday boost (see docs/learn/07-categories-and-priorities.md) --------

/** item id -> category name, built once from the tree. */
val categoryOf: Map<String, String> =
    menu.children.flatMap { cat -> cat.allItems().map { it.id to cat.name } }.toMap()

/** Business priority per category. In a real app this comes from config and
 *  the calendar (holiday boost active only in season). */
fun boostFor(category: String): Double = when (category) {
    "Holiday Drinks" -> 1.0
    "Hot Coffee", "Cold Drinks" -> 0.95
    else -> 0.92
}

/** kmatch answers "how well does the text match"; this one sort applies the
 *  business priority on top. */
fun boostedSearch(engine: MenuSearch, query: String, limit: Int = 3) =
    engine.search(query, limit = 10)
        .map { it to it.score * boostFor(categoryOf.getValue(it.item.id)) }
        .sortedByDescending { it.second }
        .take(limit)

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

    // --- PART 2: holiday boost ---------------------------------------------
    println()
    println("PART 2 — category priority: Holiday Drinks boosted over regular drinks")
    println()
    for (q in listOf("mocha", "latte")) {
        val raw = engine.search(q, limit = 3)
        val boosted = boostedSearch(engine, q)
        println("query \"$q\"")
        println("  %-34s | %s".format("RAW text score (kmatch alone)", "WITH holiday boost (score × weight)"))
        for (i in 0 until maxOf(raw.size, boosted.size)) {
            val r = raw.getOrNull(i)?.let { "%d. %s (%.1f)".format(i + 1, it.item.name, it.score) } ?: ""
            val b = boosted.getOrNull(i)?.let { (hit, final) ->
                "%d. %s (%.1f × %.2f = %.1f)".format(i + 1, hit.item.name, hit.score,
                    boostFor(categoryOf.getValue(hit.item.id)), final)
            } ?: ""
            println("  %-34s | %s".format(r, b))
        }
        println()
    }
}
