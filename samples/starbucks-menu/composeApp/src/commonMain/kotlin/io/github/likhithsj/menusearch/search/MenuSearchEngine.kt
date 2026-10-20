package io.github.likhithsj.menusearch.search

import io.github.likhithsj.kmatch.Scorers
import io.github.likhithsj.kmatch.extractAll
import io.github.likhithsj.kmatch.matchingRanges
import io.github.likhithsj.kmatch.searchFold
import io.github.likhithsj.menusearch.menu.MenuCatalog
import io.github.likhithsj.menusearch.menu.MenuItem

/**
 * The entire kmatch integration. Three decisions, same as
 * samples/menu-search and docs/learn/07:
 *
 *  a) WHAT text represents an item   -> [searchKeys]: several SHORT keys per item
 *     (the name; name + category word when the name lacks it; each nickname
 *     on its own). Scored in one scan; an item takes its best key.
 *  b) WHAT counts as the same text   -> [searchFold]: accent fold + lowercase + strip punctuation
 *     ("caffe" finds "Caffè", "Frappuccino®" loses its ®)
 *  c) WHERE the quality bar sits     -> scoreCutoff 55 on WRatio
 *
 * Then one ranking rule the library deliberately knows nothing about: a soft
 * category boost (0.9–1.0 band) so a featured/seasonal drink wins a tie with
 * its everyday twin, but never outranks a clearly better text match.
 *
 * Why several keys instead of one long "name + category + aliases" string:
 * WRatio scales partial matches down (0.9 -> 0.6) once the longer side is
 * 8× the query, so a 4-letter nickname against a 60-char key scores 60 and
 * loses to accidental 3-of-4-letter matches in short names. Short keys keep
 * every signal at full strength.
 *
 * Two scans, two scorers: product names use WRatio (typo-tolerant, partial
 * matches welcome: "mocha" should find "Caffè Mocha"); nicknames use
 * TokenSetRatio, which wants whole words ("psl" must not light up inside
 * "plumbing"). Each key type also carries a weight -- a category word is a
 * weaker description of an item than its own name -- blended in exactly like
 * the category boost.
 */
class MenuSearchEngine(
    catalog: MenuCatalog,
    private val boostFor: (MenuItem) -> Double = ::defaultBoost,
) {
    data class Hit(
        val item: MenuItem,
        /** kmatch's text score for the item's winning key, 0–100, RapidFuzz-identical. */
        val textScore: Double,
        /** textScore × key weight × category boost: what the list is sorted by. */
        val rankScore: Double,
        /** Score of the bare name alone (0 if under the cutoff): tie-breaker. */
        val nameScore: Double,
        /** Char ranges in [MenuItem.name] to highlight, or empty. */
        val highlights: List<IntRange>,
    )

    enum class KeyKind(val weight: Double) {
        /** The product name as displayed. */
        NAME(1.0),
        /** Name + leaf category word: "Pike Place® Roast Medium Roast". */
        CATEGORY(0.95),
        /** A nickname people type: "frap", "psl". Scored as whole tokens. */
        ALIAS(1.0),
    }

    data class SearchKey(val text: String, val kind: KeyKind)

    private class Key(val item: MenuItem, val key: SearchKey)

    private val textKeys: List<Key>
    private val aliasKeys: List<Key>

    init {
        val all = catalog.items.flatMap { item -> searchKeys(item).map { Key(item, it) } }
        textKeys = all.filter { it.key.kind != KeyKind.ALIAS }
        aliasKeys = all.filter { it.key.kind == KeyKind.ALIAS }
    }

    fun search(query: String, limit: Int = 40): List<Hit> {
        val q = query.trim()
        if (q.length < MIN_QUERY_LENGTH) return emptyList()

        // Names and category keys: WRatio. Nicknames: whole-token scorer.
        val scored =
            extractAll(q, textKeys, keySelector = { it.key.text }, processor = ::searchFold, scoreCutoff = SCORE_CUTOFF) +
            extractAll(q, aliasKeys, keySelector = { it.key.text }, scorer = Scorers.TokenSetRatio, processor = ::searchFold, scoreCutoff = SCORE_CUTOFF)

        class Acc(val item: MenuItem) { var best = 0.0; var bestRaw = 0.0; var name = 0.0 }
        val byItem = LinkedHashMap<String, Acc>()
        for (r in scored) {
            val key = r.item
            val acc = byItem.getOrPut(key.item.id) { Acc(key.item) }
            val weighted = r.score * key.key.kind.weight
            if (weighted > acc.best) { acc.best = weighted; acc.bestRaw = r.score }
            if (key.key.kind == KeyKind.NAME) acc.name = r.score
        }

        return byItem.values
            .map { acc ->
                Hit(
                    item = acc.item,
                    textScore = acc.bestRaw,
                    rankScore = acc.best * boostFor(acc.item),
                    nameScore = acc.name,
                    highlights = highlightRanges(q, acc.item.name),
                )
            }
            .sortedWith(
                compareByDescending<Hit> { it.rankScore }
                    .thenByDescending { it.nameScore }     // a name match beats a category/alias match
                    .thenBy { it.item.displayOrder }
                    .thenBy { it.item.name },
            )
            .take(limit)
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
        const val SCORE_CUTOFF = 55.0

        /** Nicknames people TYPE, attached to every item whose folded name contains the needle. */
        val aliases: List<Pair<String, String>> = listOf(
            "frappuccino" to "frap",
            "frappuccino" to "frappe",
            "pumpkin spice latte" to "psl",
            "pumpkin cream cold brew" to "pccb",
            "shaken espresso" to "shaken",
            "americano" to "long black",
            "caffè misto" to "cafe au lait",
            "hot chocolate" to "cocoa",
            "matcha" to "green tea",
            "egg bites" to "sous vide",
            "cake pop" to "cakepop",
            "impossible" to "plant based",
            "nitro" to "nitrogen",
        )

        /**
         * Keys for one item, bare name first. The category word is added only
         * when the name lacks it ("Cappuccino" under "Cappuccino" gains
         * nothing; "Pike Place® Roast" under "Medium Roast" gains "medium
         * roast") and never from the featured section ("Trending" is a shelf,
         * not a description).
         */
        fun searchKeys(item: MenuItem): List<SearchKey> {
            val name = item.name
            val folded = searchFold(name)
            val out = ArrayList<SearchKey>(3)
            out += SearchKey(name, KeyKind.NAME)
            val category = item.primaryCategory
            if (category.top != MenuItem.FEATURED_TOP_LEVEL && !folded.contains(searchFold(category.leaf))) {
                out += SearchKey("$name ${category.leaf}", KeyKind.CATEGORY)
            }
            for ((needle, alias) in aliases) {
                if (folded.contains(searchFold(needle))) out += SearchKey(alias, KeyKind.ALIAS)
            }
            return out
        }

        /** Soft boost in the 0.9–1.0 band (docs/learn/07, "Flavor B"). */
        fun defaultBoost(item: MenuItem): Double = when {
            item.isFeatured -> 1.0
            item.productType.equals("Beverage", ignoreCase = true) -> 0.97
            item.primaryCategory.top == "At Home Coffee" -> 0.90
            else -> 0.95
        }

        /**
         * Ranges into the DISPLAYED name. Highlighting runs on lowercase text
         * so indices line up with the original; when lowercasing changes the
         * length (rare scripts) we show no highlight rather than a wrong one.
         */
        fun highlightRanges(query: String, name: String): List<IntRange> {
            val lowerName = name.lowercase()
            if (lowerName.length != name.length) return emptyList()
            return matchingRanges(query.lowercase(), lowerName)
        }
    }
}
