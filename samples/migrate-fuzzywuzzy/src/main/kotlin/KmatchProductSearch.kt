import io.github.likhithsj.kmatch.ExtractedItem
import io.github.likhithsj.kmatch.extractSorted
import io.github.likhithsj.kmatch.searchFold

// ---------------------------------------------------------------------------
// AFTER: the same public signature, kmatch inside.
//
//   * products go in whole; keySelector tells kmatch which text to score
//   * ExtractedItem<ProductForm> comes back -- .item IS the scored form,
//     no name lookup, duplicates impossible to confuse
//   * processor folds accents, so "jalapeno"/"caffe" match "Jalapeño"/"Caffè"
//   * the Double score survives for ranking/boosting on top
// ---------------------------------------------------------------------------

object KmatchProductSearch {
    const val CUTOFF = 60.0

    fun search(query: String, products: List<ProductForm>): List<ProductForm> =
        searchScored(query, products).map { it.item }

    /** Same search, score kept -- what category boosts/rankers build on. */
    fun searchScored(query: String, products: List<ProductForm>): List<ExtractedItem<ProductForm>> =
        extractSorted(
            query,
            products,
            keySelector = { it.name },
            processor = ::searchFold,
            scoreCutoff = CUTOFF,
        )

}
// The accent fold is the library's own `searchFold` since kmatch 1.0.0.
