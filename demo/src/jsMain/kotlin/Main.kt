import io.github.likhithsj.kmatch.Fuzz
import io.github.likhithsj.kmatch.Scorer
import io.github.likhithsj.kmatch.Scorers
import io.github.likhithsj.kmatch.defaultProcess
import io.github.likhithsj.kmatch.extractSorted
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

    // Diacritic folding in two layers, the same shape as ICU's Latin-ASCII:
    // NFD decomposition strips every base+accent letter generically
    // ("São" -> "Sao", "Zürich" -> "Zurich"), and an explicit table covers
    // the Latin letters that don't decompose (ø, ł, æ, ß, þ, ...). A custom
    // processor like this is how diacritic-insensitive search composes with
    // the parity core (which, matching RapidFuzz, never folds on its own).
    val nonDecomposable = mapOf(
        'ø' to "o", 'Ø' to "O", 'æ' to "ae", 'Æ' to "AE", 'œ' to "oe", 'Œ' to "OE",
        'ß' to "ss", 'ł' to "l", 'Ł' to "L", 'đ' to "d", 'Đ' to "D",
        'ð' to "d", 'Ð' to "D", 'þ' to "th", 'Þ' to "TH",
        'ħ' to "h", 'Ħ' to "H", 'ŧ' to "t", 'Ŧ' to "T", 'ı' to "i",
    )
    // NFKD also folds compatibility forms (full-width Ｔｏｋｙｏ -> Tokyo).
    // Marks stripped: Latin/Greek/Cyrillic combining accents, Hebrew points
    // and cantillation, Arabic harakat -- scripts where marks are optional
    // vocalization. Indic scripts are left alone: their marks ARE the vowels.
    fun foldDiacritics(s: String): String {
        val stripped = (s.asDynamic().normalize("NFKD") as String)
            .replace(Regex("[\\u0300-\\u036F\\u0591-\\u05C7\\u064B-\\u065F\\u0670]"), "")
        if (stripped.none { it in nonDecomposable }) return stripped
        return buildString(stripped.length) {
            for (ch in stripped) append(nonDecomposable[ch] ?: ch.toString())
        }
    }

    fun processor(): ((String) -> String)? {
        val fold = foldBox.checked
        val prep = pre.checked
        return when {
            fold && prep -> { s -> defaultProcess(foldDiacritics(s)) }
            fold -> ::foldDiacritics
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

    fun renderAll() {
        renderPairwise()
        renderExtraction()
    }

    for (input in listOf(s1, s2, pre, foldBox, query, cutoff)) {
        input.addEventListener("input", { renderAll() })
    }
    choices.addEventListener("input", { renderExtraction() })
    scorerSel.addEventListener("change", { renderExtraction() })

    renderAll()
}
