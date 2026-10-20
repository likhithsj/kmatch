package io.github.likhithsj.menusearch.menu

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Wire format: exactly what https://www.starbucks.com/apiproxy/v1/ordering/menu
// returns (the JSON behind the starbucks.com menu page). Every field is
// optional-with-default so a field the site adds or drops tomorrow cannot
// break parsing; `ignoreUnknownKeys` covers the rest.
// ---------------------------------------------------------------------------

@Serializable
data class MenuSnapshot(
    val fetchedAt: String? = null,
    val sourceUrl: String? = null,
    val menu: MenuResponse,
)

@Serializable
data class MenuResponse(val menus: List<CategoryDto> = emptyList())

@Serializable
data class CategoryDto(
    val name: String = "",
    val id: String? = null,
    val uri: String? = null,
    val displayOrder: Int = 0,
    val categoryImageURL: String? = null,
    val heroCategoryImageURL: String? = null,
    val products: List<ProductDto> = emptyList(),
    val children: List<CategoryDto> = emptyList(),
)

@Serializable
data class ProductDto(
    val name: String = "",
    val productNumber: Long = 0,
    val formCode: String? = null,
    val productType: String? = null,
    val availability: String? = null,
    val imageURL: String? = null,
    val displayOrder: Int = 0,
    val uri: String? = null,
    val sizes: List<SizeDto> = emptyList(),
)

@Serializable
data class SizeDto(val sizeCode: String? = null)

// ---------------------------------------------------------------------------
// Domain model: the tree flattened once into search-ready items. The same
// product appears under several categories on the site ("Trending" repeats
// drinks that also live under "Latte"), so items are keyed by product number
// + form and remember EVERY category path they were found under.
// ---------------------------------------------------------------------------

data class CategoryPath(val segments: List<String>) {
    val top: String get() = segments.first()
    val leaf: String get() = segments.last()
    val display: String get() = segments.asReversed().joinToString(" › ")
}

data class MenuItem(
    val id: String,
    val name: String,
    val form: String?,          // "Iced", "Hot", or null for food/merch
    val productType: String?,   // "Beverage" / "Food" / ...
    val imageUrl: String?,
    val sizes: List<String>,
    val categories: List<CategoryPath>,
    val displayOrder: Int,
    val webPath: String?,       // "/product/41292/iced" on starbucks.com
) {
    val primaryCategory: CategoryPath get() = categories.first()
    val isFeatured: Boolean get() = categories.any { it.top == FEATURED_TOP_LEVEL }
    val webUrl: String? get() = webPath?.let { "https://www.starbucks.com/menu$it" }

    companion object {
        const val FEATURED_TOP_LEVEL = "The Latest"
    }
}

/** A browsable node: the site's category tree with products resolved to items. */
data class BrowseNode(
    val path: CategoryPath,
    val imageUrl: String?,
    val items: List<MenuItem>,
    val children: List<BrowseNode>,
) {
    val name: String get() = path.leaf
    fun allItems(): List<MenuItem> = (items + children.flatMap { it.allItems() }).distinctBy { it.id }
}

data class MenuCatalog(
    val items: List<MenuItem>,
    val tree: List<BrowseNode>,
    val fetchedAt: String?,
    val sourceUrl: String?,
) {
    companion object {
        fun from(snapshot: MenuSnapshot): MenuCatalog = from(snapshot.menu, snapshot.fetchedAt, snapshot.sourceUrl)

        fun from(response: MenuResponse, fetchedAt: String?, sourceUrl: String?): MenuCatalog {
            val byId = LinkedHashMap<String, MenuItem>()

            fun register(p: ProductDto, path: CategoryPath): MenuItem {
                val id = "${p.productNumber}:${p.formCode.orEmpty().lowercase()}"
                val existing = byId[id]
                val merged = if (existing == null) {
                    MenuItem(
                        id = id,
                        name = p.name.trim(),
                        form = p.formCode?.takeIf { it.isNotBlank() },
                        productType = p.productType,
                        imageUrl = p.imageURL?.takeIf { it.isNotBlank() },
                        sizes = p.sizes.mapNotNull { it.sizeCode?.takeIf(String::isNotBlank) },
                        categories = listOf(path),
                        displayOrder = p.displayOrder,
                        webPath = p.uri,
                    )
                } else {
                    existing.copy(categories = existing.categories + path)
                }
                byId[id] = merged
                return merged
            }

            fun build(dto: CategoryDto, parents: List<String>): BrowseNode {
                val path = CategoryPath(parents + dto.name.trim())
                val items = dto.products.sortedBy { it.displayOrder }.map { register(it, path) }
                val children = dto.children.sortedBy { it.displayOrder }.map { build(it, path.segments) }
                return BrowseNode(path, dto.categoryImageURL ?: dto.heroCategoryImageURL, items, children)
            }

            // Featured sections ("The Latest") come first on the site and are
            // registered first here, so their path is never an item's PRIMARY
            // category: flip the order so the real home category wins.
            val tree = response.menus.sortedBy { it.displayOrder }.map { build(it, emptyList()) }
            val items = byId.values.map { item ->
                val home = item.categories.firstOrNull { it.top != MenuItem.FEATURED_TOP_LEVEL }
                if (home == null || item.categories.first() == home) item
                else item.copy(categories = listOf(home) + (item.categories - home))
            }
            // Re-point tree nodes at the final (merged) items so every node shares one instance.
            val finalById = items.associateBy { it.id }
            fun relink(n: BrowseNode): BrowseNode =
                n.copy(items = n.items.map { finalById.getValue(it.id) }, children = n.children.map(::relink))
            return MenuCatalog(items, tree.map(::relink), fetchedAt, sourceUrl)
        }
    }
}
