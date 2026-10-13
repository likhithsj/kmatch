// Side-by-side table over queries a real user types. Each cell shows the
// forms that came back as "number/formCode name" -- identity visible, so the
// duplicate-name bug is visible too.

fun main() {
    val queries = listOf(
        "caffe latte",     // no accent typed + duplicate-named forms
        "latte",           // short query
        "jalapeno bagel",  // no accent typed
        "carmel machiato", // two typos
        "cold brew",
        "dragon fruit",    // spacing differs
    )

    fun fmt(p: ProductForm) = "${p.productNumber}/${p.formCode} ${p.name}"

    println("%-18s | %-44s | %s".format("QUERY", "LEGACY fuzzywuzzy (string round-trip)", "KMATCH (typed, accent-folded)"))
    println("-".repeat(130))
    for (q in queries) {
        val legacy = LegacyProductSearch.search(q, catalog).take(3)
        val kmatch = KmatchProductSearch.searchScored(q, catalog).take(3)
        val rows = maxOf(legacy.size, kmatch.size, 1)
        for (i in 0 until rows) {
            val l = legacy.getOrNull(i)?.let { fmt(it) } ?: if (i == 0) "(no result over cutoff)" else ""
            val k = kmatch.getOrNull(i)?.let { "%s  (%.1f)".format(fmt(it.item), it.score) }
                ?: if (i == 0) "(no result over cutoff)" else ""
            println("%-18s | %-44s | %s".format(if (i == 0) q else "", l, k))
        }
        println("-".repeat(130))
    }
    println()
    println("Watch the \"caffe latte\" row: legacy returns 2121/HOT twice -- the ICED form")
    println("can never be a result, because object identity was rebuilt by name equality.")
}
