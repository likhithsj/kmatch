package io.github.likhithsj.menusearch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.likhithsj.menusearch.menu.BrowseNode
import io.github.likhithsj.menusearch.menu.MenuItem
import io.github.likhithsj.menusearch.menu.MenuRepository
import io.github.likhithsj.menusearch.search.MenuSearchEngine
import kotlin.math.roundToInt

private val Espresso = Color(0xFF3B2A20)
private val Leaf = Color(0xFF1E6B45)
private val Cream = Color(0xFFF7F3EC)

@Composable
fun App(isDark: Boolean = false) {
    val colors = if (isDark) {
        darkColorScheme(primary = Color(0xFF7ED3A3), secondary = Color(0xFFD9C3A5), background = Color(0xFF17120F), surface = Color(0xFF1F1915))
    } else {
        lightColorScheme(primary = Leaf, secondary = Espresso, background = Cream, surface = Color.White)
    }
    MaterialTheme(colorScheme = colors) {
        val scope = rememberCoroutineScope()
        val state = remember { AppState(MenuRepository(), scope) }
        LaunchedEffect(Unit) { state.start() }
        MenuSearchScreen(state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSearchScreen(state: AppState) {
    val category = state.selectedCategory
    val hits = remember(state.query, state.engine) { state.results }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(category?.name ?: "Menu Search", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (category != null) category.path.display else "Starbucks® menu · fuzzy search by kmatch",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    if (category != null) {
                        IconButton(onClick = { state.selectedCategory = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to all categories")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = state::refresh, enabled = !state.isRefreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh from starbucks.com")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(
                query = state.query,
                onQueryChange = { state.query = it },
                placeholder = if (category != null) "Search in ${category.name}" else "Try “pumkin spice”, “frap” or “caffe latte”",
            )
            StatusLine(state)
            val catalog = state.catalog
            when {
                catalog == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.isSearching -> ResultsList(
                    hits = hits.let { hits ->
                        if (category == null) hits
                        else {
                            val inScope = category.allItems().mapTo(HashSet()) { it.id }
                            hits.filter { it.item.id in inScope }
                        }
                    },
                    query = state.query,
                    onClick = { state.selectedItem = it },
                )
                category != null -> CategoryContents(category, onOpenCategory = { state.selectedCategory = it }, onClick = { state.selectedItem = it })
                else -> CategoryIndex(catalog.tree, onOpenCategory = { state.selectedCategory = it })
            }
        }
    }

    state.selectedItem?.let { item ->
        ModalBottomSheet(onDismissRequest = { state.selectedItem = null }) {
            ItemDetail(item)
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
    )
}

@Composable
private fun StatusLine(state: AppState) {
    val (icon, text) = when (val s = state.source) {
        DataSource.Loading -> Icons.Outlined.Inventory2 to "Loading menu…"
        is DataSource.Bundled -> Icons.Outlined.Inventory2 to "Bundled snapshot${formatSnapshotDate(s.fetchedAt)?.let { " from $it" }.orEmpty()}"
        DataSource.Live -> Icons.Outlined.CloudDone to "Live menu from starbucks.com"
        is DataSource.LiveFailed -> Icons.Outlined.CloudOff to "Offline · showing snapshot${formatSnapshotDate(s.fallback.fetchedAt)?.let { " from $it" }.orEmpty()}"
    }
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.catalog?.let {
                Spacer(Modifier.weight(1f))
                Text("${it.items.size} items", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.isRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp)) else Spacer(Modifier.height(2.dp))
    }
}

// --- Search results ---------------------------------------------------------

@Composable
private fun ResultsList(hits: List<MenuSearchEngine.Hit>, query: String, onClick: (MenuItem) -> Unit) {
    if (hits.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Nothing close to “${query.trim()}”.\nTry fewer words, or a nickname like “frap”.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
        items(hits, key = { it.item.id }) { hit ->
            ResultRow(hit, onClick = { onClick(hit.item) })
            HorizontalDivider(Modifier.padding(start = 88.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ResultRow(hit: MenuSearchEngine.Hit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(hit.item, size = 56.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(highlighted(hit.item.name, hit.highlights), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(hit.item.form, hit.item.primaryCategory.display).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        ScoreBadge(hit)
    }
}

/** The match score, visible on purpose: this is a search-library demo. */
@Composable
private fun ScoreBadge(hit: MenuSearchEngine.Hit) {
    val boosted = hit.rankScore.roundToInt() != hit.textScore.roundToInt()
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(hit.textScore.roundToInt().toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (boosted) Text("→${hit.rankScore.roundToInt()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun highlighted(name: String, ranges: List<IntRange>): AnnotatedString {
    val style = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    return AnnotatedString.Builder(name).apply {
        for (r in ranges) {
            val end = (r.last + 1).coerceAtMost(name.length)
            if (r.first in 0 until end) addStyle(style, r.first, end)
        }
    }.toAnnotatedString()
}

// --- Browse mode ------------------------------------------------------------

@Composable
private fun CategoryIndex(tree: List<BrowseNode>, onOpenCategory: (BrowseNode) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        items(tree, key = { it.path.segments.joinToString("/") }) { top ->
            Text(
                top.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp).clickable { onOpenCategory(top) },
            )
            val leaves = top.walk().filter { it.items.isNotEmpty() }.toList()
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(leaves, key = { it.path.segments.joinToString("/") }) { leaf -> CategoryCard(leaf, onClick = { onOpenCategory(leaf) }) }
            }
        }
    }
}

@Composable
private fun CategoryCard(node: BrowseNode, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(140.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        val image = node.imageUrl ?: node.items.firstNotNullOfOrNull { it.imageUrl }
        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (image != null) AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(node.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp).height(36.dp))
    }
}

@Composable
private fun CategoryContents(node: BrowseNode, onOpenCategory: (BrowseNode) -> Unit, onClick: (MenuItem) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (node.children.isNotEmpty()) {
            item {
                LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(node.children, key = { it.path.segments.joinToString("/") }) { child ->
                        AssistChip(onClick = { onOpenCategory(child) }, label = { Text(child.name) })
                    }
                }
            }
        }
        val all = node.allItems()
        items(all, key = { it.id }) { item ->
            Row(Modifier.fillMaxWidth().clickable { onClick(item) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Thumb(item, 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(listOfNotNull(item.form, item.sizes.takeIf { it.isNotEmpty() }?.joinToString(", ")).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(start = 88.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

// --- Detail -----------------------------------------------------------------

@Composable
private fun ItemDetail(item: MenuItem) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
        item.imageUrl?.let {
            AsyncImage(
                model = displayImage(it),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.height(16.dp))
        }
        Text(item.name, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            listOfNotNull(item.form, item.productType).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.sizes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Sizes", style = MaterialTheme.typography.labelLarge)
            Text(item.sizes.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text(if (item.categories.size > 1) "Found under" else "Category", style = MaterialTheme.typography.labelLarge)
        item.categories.forEach { Text(it.display, style = MaterialTheme.typography.bodyMedium) }
        item.webUrl?.let { url ->
            Spacer(Modifier.height(16.dp))
            AssistChip(onClick = { uriHandler.openUri(url) }, label = { Text("Open on starbucks.com") })
        }
    }
}

@Composable
private fun Thumb(item: MenuItem, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        item.imageUrl?.let {
            AsyncImage(model = displayImage(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Starbucks' image CDN serves a padded original; this policy crops to a square, web-sized render. */
private fun displayImage(url: String): String =
    if (url.contains('?')) url else "$url?impolicy=1by1_wide_topcrop_630"
