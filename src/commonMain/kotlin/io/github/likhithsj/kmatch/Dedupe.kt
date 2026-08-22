package io.github.likhithsj.kmatch

/**
 * Collapses near-duplicate items: any two items whose keys score at or above
 * [threshold] under [scorer] are considered duplicates of each other, and one
 * canonical representative per duplicate group is kept.
 *
 * Mirrors fuzzywuzzy `process.dedupe` semantics:
 *
 * - the representative of a group is its longest key (by code points), ties
 *   broken by lexicographically smallest key, then by input order;
 * - output preserves the input order of first appearance of each group;
 * - with no duplicates, the input items are returned unchanged.
 *
 * The default scorer is [Scorers.TokenSetRatio], which treats reordered and
 * subset token sets as identical ("Frodo Baggins" vs "Baggins, Frodo").
 * Comparison is O(n^2) in the number of items.
 *
 * @param processor applied to keys before scoring (e.g. [defaultProcess]);
 *   representatives are always original items.
 */
public fun <T> dedupe(
    items: Iterable<T>,
    keySelector: (T) -> String,
    threshold: Double = 70.0,
    scorer: Scorer = Scorers.TokenSetRatio,
    processor: ((String) -> String)? = null,
): List<T> {
    val itemList = items.toList()
    if (itemList.size < 2) return itemList

    val keys = itemList.map(keySelector)
    val processedKeys =
        if (processor != null) keys.map(processor) else keys
    val processedCps = processedKeys.map { it.toCodePoints() }
    val keyCps = keys.map { it.toCodePoints() }

    // Every item gets a duplicate group: all items scoring >= threshold
    // against it. Each group elects one representative; the output is the
    // unique representatives in order of first election -- exactly
    // fuzzywuzzy's algorithm. Prepared scorers reuse each row's match masks
    // over the scan.
    val out = ArrayList<T>()
    val chosen = HashSet<Int>()
    for (i in itemList.indices) {
        val prepared = prepareScorer(scorer, processedCps[i])
        // Canonical representative: longest key (in code points), then
        // smallest in code-point order (Python string ordering, which
        // differs from UTF-16 order for astral-plane characters), then
        // earliest input position.
        var rep = -1
        for (j in itemList.indices) {
            val score =
                if (prepared != null) prepared.score(processedCps[j], threshold)
                else scorer.score(processedKeys[i], processedKeys[j], threshold)
            if (score >= threshold) {
                if (rep == -1 || keyCps[j].size > keyCps[rep].size ||
                    (keyCps[j].size == keyCps[rep].size &&
                        CodePointArrayComparator.compare(keyCps[j], keyCps[rep]) < 0)
                ) rep = j
            }
        }
        // An item can match nothing at all -- not even itself: empty token
        // sets score 0 under TokenSetRatio, and empty strings score 0 under
        // QuickRatio. Such an item forms its own group rather than crashing.
        if (rep == -1) rep = i
        if (chosen.add(rep)) out.add(itemList[rep])
    }
    return out
}

/** [dedupe] over plain strings. */
public fun dedupe(
    items: Iterable<String>,
    threshold: Double = 70.0,
    scorer: Scorer = Scorers.TokenSetRatio,
    processor: ((String) -> String)? = null,
): List<String> = dedupe(items, { it }, threshold, scorer, processor)
