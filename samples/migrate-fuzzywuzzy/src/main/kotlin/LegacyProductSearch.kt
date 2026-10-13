import me.xdrop.fuzzywuzzy.FuzzySearch

// ---------------------------------------------------------------------------
// BEFORE: the fuzzywuzzy string round-trip, reproduced as found in the wild.
//
//   1. products.map { it.name }      -- typed list flattened to strings;
//                                       productNumber/formCode thrown away
//   2. FuzzySearch.extractSorted(..) -- scores strings; the result DOES carry
//                                       getIndex() and getScore(), both dropped
//   3. products.first { name == s }  -- object identity reverse-engineered by
//                                       string equality
//
// Step 3 is the bug factory: with two forms named "Caffè Latte", first { }
// always returns the same one -- the other form can never be a result.
// ---------------------------------------------------------------------------

object LegacyProductSearch {
    const val CUTOFF = 60

    fun search(query: String, products: List<ProductForm>): List<ProductForm> =
        FuzzySearch.extractSorted(query, products.map { it.name }, CUTOFF)
            .map { match -> products.first { it.name == match.string } }
}
