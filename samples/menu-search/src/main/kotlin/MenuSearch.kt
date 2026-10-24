import io.github.likhithsj.kmatch.ExtractedItem
import io.github.likhithsj.kmatch.extractTop
import io.github.likhithsj.kmatch.matchingRanges
import io.github.likhithsj.kmatch.searchFold

// ---------------------------------------------------------------------------
// 1. THE MENU MODEL -- a category tree with keyword maps, the shape a real
//    app already has. kmatch needs to know nothing about this structure.
// ---------------------------------------------------------------------------

data class MenuItem(
    val id: String,
    val name: String,
    val keywords: List<String>, // merchandising/alias keywords, per item
)

data class MenuCategory(
    val name: String,
    val items: List<MenuItem> = emptyList(),
    val children: List<MenuCategory> = emptyList(),
)

/** Flatten the tree once; re-do only when the menu changes. */
fun MenuCategory.allItems(): List<MenuItem> =
    items + children.flatMap { it.allItems() }

// ---------------------------------------------------------------------------
// 2. THE SEARCH ENGINE -- the entire kmatch integration. Three decisions:
//
//    a) WHAT text represents an item  -> keySelector (name + keywords)
//    b) WHAT counts as the same text  -> processor (fold accents, lowercase,
//       strip punctuation)
//    c) WHERE the quality bar sits    -> scoreCutoff
//
//    Processing flow per query: the query string is processed once, its
//    match masks are built once, then every item's key is processed and
//    scored against those masks (bit-parallel), items under the cutoff are
//    dropped, the rest come back ranked with your MenuItem inside.
// ---------------------------------------------------------------------------

class MenuSearch(menu: MenuCategory) {

    private val items = menu.allItems()

    fun search(query: String, limit: Int = 5): List<ExtractedItem<MenuItem>> =
        extractTop(
            query,
            items,
            keySelector = { "${it.name} ${it.keywords.joinToString(" ")}" },
            limit = limit,
            processor = ::searchFold,
            scoreCutoff = 55.0,
        )

    /** For UI highlighting of a result row. */
    fun highlight(query: String, item: MenuItem): List<IntRange> =
        matchingRanges(query.lowercase(), item.name.lowercase())

}
// The accent fold ("jalapeño" -> "jalapeno" + defaultProcess) used to live
// here as a hand-rolled helper; since kmatch 1.0.0 it is the library's own
// `searchFold`, imported above.
