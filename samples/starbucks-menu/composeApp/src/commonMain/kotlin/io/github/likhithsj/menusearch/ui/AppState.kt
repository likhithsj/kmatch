package io.github.likhithsj.menusearch.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.likhithsj.menusearch.menu.BrowseNode
import io.github.likhithsj.menusearch.menu.MenuCatalog
import io.github.likhithsj.menusearch.menu.MenuItem
import io.github.likhithsj.menusearch.menu.MenuRepository
import io.github.likhithsj.menusearch.search.MenuSearchEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Where the catalog on screen came from, for the status line. */
sealed interface DataSource {
    data object Loading : DataSource
    data class Bundled(val fetchedAt: String?) : DataSource
    data object Live : DataSource
    data class LiveFailed(val reason: String, val fallback: Bundled) : DataSource
}

/**
 * Plain state holder (no ViewModel dependency, so the shared code stays
 * identical on both platforms). The screen is a function of these fields.
 */
class AppState(
    private val repository: MenuRepository,
    private val scope: CoroutineScope,
) {
    var catalog: MenuCatalog? by mutableStateOf(null)
        private set
    var engine: MenuSearchEngine? by mutableStateOf(null)
        private set
    var source: DataSource by mutableStateOf(DataSource.Loading)
        private set
    var isRefreshing: Boolean by mutableStateOf(false)
        private set

    var query: String by mutableStateOf("")
    var selectedCategory: BrowseNode? by mutableStateOf(null)
    var selectedItem: MenuItem? by mutableStateOf(null)

    val results: List<MenuSearchEngine.Hit>
        get() = engine?.search(query).orEmpty()

    val isSearching: Boolean get() = query.trim().length >= MenuSearchEngine.MIN_QUERY_LENGTH

    fun start() {
        scope.launch {
            val bundled = runCatching { repository.loadBundled() }.getOrNull()
            if (bundled != null) install(bundled, DataSource.Bundled(bundled.fetchedAt))
            refresh()
        }
    }

    fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        scope.launch {
            try {
                val live = repository.fetchLive()
                install(live, DataSource.Live)
            } catch (e: Exception) {
                val fallback = (source as? DataSource.Bundled)
                    ?: (source as? DataSource.LiveFailed)?.fallback
                    ?: DataSource.Bundled(catalog?.fetchedAt)
                source = DataSource.LiveFailed(e.message ?: e::class.simpleName.orEmpty(), fallback)
            } finally {
                isRefreshing = false
            }
        }
    }

    private fun install(newCatalog: MenuCatalog, newSource: DataSource) {
        catalog = newCatalog
        engine = MenuSearchEngine(newCatalog)
        source = newSource
        // Keep a selected category pointing at the fresh tree, if it still exists.
        selectedCategory = selectedCategory?.let { old ->
            newCatalog.tree.asSequence().flatMap { it.walk() }.firstOrNull { it.path == old.path }
        }
    }
}

fun BrowseNode.walk(): Sequence<BrowseNode> = sequence {
    yield(this@walk)
    children.forEach { yieldAll(it.walk()) }
}

/** "2026-10-20T20:47:04Z" -> "Oct 20, 2026" without a date library. */
fun formatSnapshotDate(iso: String?): String? {
    if (iso == null || iso.length < 10) return null
    val year = iso.substring(0, 4)
    val month = iso.substring(5, 7).toIntOrNull() ?: return null
    val day = iso.substring(8, 10).toIntOrNull() ?: return null
    val names = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return "${names.getOrNull(month - 1) ?: return null} $day, $year"
}
