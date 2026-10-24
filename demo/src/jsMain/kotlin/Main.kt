import io.github.likhithsj.kmatch.Fuzz
import io.github.likhithsj.kmatch.Scorer
import io.github.likhithsj.kmatch.Scorers
import io.github.likhithsj.kmatch.defaultProcess
import io.github.likhithsj.kmatch.extractSorted
import io.github.likhithsj.kmatch.searchFold
import io.github.likhithsj.kmatch.stripAccents
import io.github.likhithsj.kmatch.matchingRanges
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTableElement
import org.w3c.dom.HTMLTextAreaElement

private data class NamedScorer(val name: String, val scorer: Scorer)

private val scorers = listOf(
    NamedScorer("ratio", Scorers.Ratio),
    NamedScorer("partialRatio", Scorers.PartialRatio),
    NamedScorer("tokenSortRatio", Scorers.TokenSortRatio),
    NamedScorer("tokenSetRatio", Scorers.TokenSetRatio),
    NamedScorer("tokenRatio", Scorers.TokenRatio),
    NamedScorer("partialTokenSortRatio", Scorers.PartialTokenSortRatio),
    NamedScorer("partialTokenSetRatio", Scorers.PartialTokenSetRatio),
    NamedScorer("partialTokenRatio", Scorers.PartialTokenRatio),
    NamedScorer("weightedRatio", Scorers.WeightedRatio),
    NamedScorer("quickRatio", Scorers.QuickRatio),
)

private val pairwise = listOf<Pair<String, (String, String, ((String) -> String)?) -> Double>>(
    "ratio" to { a, b, p -> Fuzz.ratio(a, b, processor = p) },
    "partialRatio" to { a, b, p -> Fuzz.partialRatio(a, b, processor = p) },
    "tokenSortRatio" to { a, b, p -> Fuzz.tokenSortRatio(a, b, processor = p) },
    "tokenSetRatio" to { a, b, p -> Fuzz.tokenSetRatio(a, b, processor = p) },
    "tokenRatio" to { a, b, p -> Fuzz.tokenRatio(a, b, processor = p) },
    "partialTokenSortRatio" to { a, b, p -> Fuzz.partialTokenSortRatio(a, b, processor = p) },
    "partialTokenSetRatio" to { a, b, p -> Fuzz.partialTokenSetRatio(a, b, processor = p) },
    "partialTokenRatio" to { a, b, p -> Fuzz.partialTokenRatio(a, b, processor = p) },
    "weightedRatio" to { a, b, p -> Fuzz.weightedRatio(a, b, processor = p) },
    "quickRatio" to { a, b, p -> Fuzz.quickRatio(a, b, processor = p) },
)

private fun el(id: String): HTMLElement = document.getElementById(id) as HTMLElement

private fun Double.fmt(): String = asDynamic().toFixed(2) as String

fun main() {
    val s1 = el("s1") as HTMLInputElement
    val s2 = el("s2") as HTMLInputElement
    val pre = el("pre") as HTMLInputElement
    val foldBox = el("fold") as HTMLInputElement
    val table = el("score-table") as HTMLTableElement
    val query = el("query") as HTMLInputElement
    val scorerSel = el("scorer") as HTMLSelectElement
    val cutoff = el("cutoff") as HTMLInputElement
    val choices = el("choices") as HTMLTextAreaElement
    val results = el("results")

    // Scorer dropdown; weightedRatio is the extraction default in the library.
    for (s in scorers) {
        val opt = document.createElement("option")
        opt.textContent = s.name
        if (s.name == "weightedRatio") opt.setAttribute("selected", "")
        scorerSel.appendChild(opt)
    }

    // One table row per scorer: name | bar | value.
    val bars = HashMap<String, HTMLElement>()
    val vals = HashMap<String, HTMLElement>()
    for ((name, _) in pairwise) {
        val tr = document.createElement("tr")
        val tdName = document.createElement("td").apply { className = "name"; textContent = name }
        val tdBar = document.createElement("td").apply { className = "bar-cell" }
        val bar = document.createElement("div").apply { className = "bar" }
        val fill = document.createElement("i") as HTMLElement
        bar.appendChild(fill)
        tdBar.appendChild(bar)
        val tdVal = document.createElement("td").apply { className = "val" } as HTMLElement
        tr.appendChild(tdName); tr.appendChild(tdBar); tr.appendChild(tdVal)
        table.appendChild(tr)
        bars[name] = fill
        vals[name] = tdVal
    }

    // Since 1.0.0 the diacritic fold is the library's own: stripAccents is
    // the fold alone (NFKD + optional-mark stripping + non-decomposable Latin
    // letters), searchFold adds defaultProcess on top. The checkboxes map
    // straight onto the published API.
    fun processor(): ((String) -> String)? {
        val fold = foldBox.checked
        val prep = pre.checked
        return when {
            fold && prep -> ::searchFold
            fold -> ::stripAccents
            prep -> ::defaultProcess
            else -> null
        }
    }

    fun renderPairwise() {
        val p = processor()
        for ((name, fn) in pairwise) {
            val score = fn(s1.value, s2.value, p)
            bars[name]!!.style.width = "$score%"
            vals[name]!!.textContent = score.fmt()
        }
    }

    // Highlighting runs on lowercased copies when lowercasing is
    // length-preserving, so char ranges stay valid on the original string.
    fun highlightRanges(q: String, choice: String): List<IntRange> {
        val ql = q.lowercase().takeIf { it.length == q.length } ?: q
        val cl = choice.lowercase().takeIf { it.length == choice.length } ?: choice
        return matchingRanges(ql, cl)
    }

    fun renderExtraction() {
        val list = choices.value.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        val cut = cutoff.value.trim().toDoubleOrNull()?.coerceIn(0.0, 100.0)
        val scorer = scorers.first { it.name == scorerSel.value }.scorer
        val hits = extractSorted(query.value, list, scorer, processor(), cut)

        results.textContent = ""
        if (hits.isEmpty()) {
            val empty = document.createElement("div").apply {
                className = "empty"
                textContent = "No choice reaches the cutoff. Lower it, or loosen the query."
            }
            results.appendChild(empty)
            return
        }
        for (hit in hits) {
            val row = document.createElement("div").apply { className = "hit" }
            val badge = document.createElement("span").apply {
                className = "score"
                textContent = hit.score.fmt()
            }
            row.appendChild(badge)
            val text = document.createElement("span")
            var at = 0
            for (r in highlightRanges(query.value, hit.choice)) {
                if (r.first > at) text.appendChild(document.createTextNode(hit.choice.substring(at, r.first)))
                val mark = document.createElement("mark")
                mark.textContent = hit.choice.substring(r.first, r.last + 1)
                text.appendChild(mark)
                at = r.last + 1
            }
            if (at < hit.choice.length) text.appendChild(document.createTextNode(hit.choice.substring(at)))
            row.appendChild(text)
            results.appendChild(row)
        }
    }

    // --- Categories & priority ---------------------------------------------
    // The pattern from samples/menu-search and docs/learn/07: the library
    // scores a FLAT list of items (category is just data each item carries);
    // priority is one multiply-and-sort applied on top, outside the library.
    val cquery = el("cquery") as HTMLInputElement
    val ccutoff = el("ccutoff") as HTMLInputElement
    val citems = el("citems") as HTMLTextAreaElement
    val boostsBox = el("boosts")
    val craw = el("craw")
    val cboosted = el("cboosted")

    // Forward reference so dynamically created boost inputs can re-render.
    var renderCategoriesRef: () -> Unit = {}

    data class CatItem(val category: String, val name: String, val keywords: String)

    fun parseMenu(): List<CatItem> =
        citems.value.split('\n').mapNotNull { line ->
            val parts = line.split('|').map { it.trim() }
            val category = parts[0].ifEmpty { "Other" }
            when {
                parts.size == 1 -> parts[0].takeIf { it.isNotEmpty() }?.let { CatItem("Other", it, "") }
                parts.size == 2 -> CatItem(category, parts[1], "")
                else -> CatItem(category, parts[1], parts.drop(2).joinToString(" ").replace(',', ' '))
            }
        }.filter { it.name.isNotEmpty() }

    // One editable boost input per category found in the textarea. Values the
    // user typed survive re-parses; new categories default to 1.00 if their
    // name says "holiday", 0.95 otherwise (the soft-boost band from guide 07).
    val boostInputs = LinkedHashMap<String, HTMLInputElement>()
    fun syncBoostInputs(categories: List<String>) {
        if (boostInputs.keys.toList() == categories) return
        val kept = boostInputs.mapValues { it.value.value }
        boostInputs.clear()
        boostsBox.textContent = ""
        for (cat in categories) {
            val inputId = "boost-" + cat.lowercase().replace(Regex("[^a-z0-9]+"), "-")
            val field = document.createElement("div").apply { className = "field narrow" }
            val label = document.createElement("label").apply {
                textContent = "$cat boost"
                setAttribute("for", inputId)
            }
            val input = (document.createElement("input") as HTMLInputElement).apply {
                type = "text"
                id = inputId
                value = kept[cat] ?: if ("holiday" in cat.lowercase()) "1.00" else "0.95"
                setAttribute("inputmode", "decimal")
                setAttribute("autocomplete", "off")
            }
            input.addEventListener("input", { renderCategoriesRef() })
            field.appendChild(label)
            field.appendChild(input)
            boostsBox.appendChild(field)
            boostInputs[cat] = input
        }
    }

    fun hitRow(target: HTMLElement, badge: String, name: String, category: String, math: String? = null) {
        val row = document.createElement("div").apply { className = "hit" }
        row.appendChild(document.createElement("span").apply { className = "score"; textContent = badge })
        row.appendChild(document.createElement("span").apply { textContent = name })
        row.appendChild(document.createElement("span").apply { className = "chip"; textContent = category })
        if (math != null) {
            row.appendChild(document.createElement("span").apply { className = "math"; textContent = math })
        }
        target.appendChild(row)
    }

    fun emptyRow(target: HTMLElement) {
        target.appendChild(document.createElement("div").apply {
            className = "empty"
            textContent = "No item reaches the cutoff."
        })
    }

    fun renderCategories() {
        val items = parseMenu()
        syncBoostInputs(items.map { it.category }.distinct())
        val cut = ccutoff.value.trim().toDoubleOrNull()?.coerceIn(0.0, 100.0)

        // ONE library call over the flat list; the searchable text per item is
        // "name + keywords" (the keySelector pattern from samples/menu-search).
        val hits = extractSorted(
            cquery.value,
            items,
            keySelector = { "${it.name} ${it.keywords}".trim() },
            processor = processor(),
            scoreCutoff = cut,
        )

        fun boostOf(category: String): Double =
            boostInputs[category]?.value?.trim()?.toDoubleOrNull()?.coerceIn(0.0, 2.0) ?: 1.0

        craw.textContent = ""
        cboosted.textContent = ""
        if (hits.isEmpty()) {
            emptyRow(craw)
            emptyRow(cboosted)
            return
        }

        // Left: what the library said (text relevance only, ties keep menu order).
        for (h in hits) hitRow(craw, h.score.fmt(), h.item.name, h.item.category)

        // Right: YOUR ranking rule — score × category weight, re-sorted.
        val ranked = hits
            .map { it to it.score * boostOf(it.item.category) }
            .sortedByDescending { it.second }
        for ((h, final) in ranked) {
            val b = boostOf(h.item.category)
            hitRow(cboosted, final.fmt(), h.item.name, h.item.category, "${h.score.fmt()} × ${b.asDynamic().toFixed(2)}")
        }
    }
    renderCategoriesRef = ::renderCategories

    fun renderAll() {
        renderPairwise()
        renderExtraction()
        renderCategories()
    }

    for (input in listOf(s1, s2, pre, foldBox, query, cutoff)) {
        input.addEventListener("input", { renderAll() })
    }
    choices.addEventListener("input", { renderExtraction() })
    scorerSel.addEventListener("change", { renderExtraction() })
    for (input in listOf(cquery, ccutoff, citems)) {
        input.addEventListener("input", { renderCategories() })
    }

    renderAll()
}
