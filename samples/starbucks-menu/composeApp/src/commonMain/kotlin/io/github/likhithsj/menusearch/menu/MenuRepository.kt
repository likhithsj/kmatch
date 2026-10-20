package io.github.likhithsj.menusearch.menu

import io.github.likhithsj.menusearch.resources.Res
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Two sources, one shape:
 *  - [loadBundled]: the snapshot checked into the app (see tools/fetch_menu.py),
 *    available instantly and offline.
 *  - [fetchLive]: the same endpoint the starbucks.com menu page calls, so the
 *    app shows today's menu when it has a network.
 */
class MenuRepository(
    private val client: HttpClient = defaultHttpClient(),
    private val json: Json = lenientJson,
) {
    @OptIn(ExperimentalResourceApi::class)
    suspend fun loadBundled(): MenuCatalog {
        val bytes = Res.readBytes("files/menu.json")
        val snapshot = json.decodeFromString(MenuSnapshot.serializer(), bytes.decodeToString())
        return MenuCatalog.from(snapshot)
    }

    suspend fun fetchLive(): MenuCatalog {
        val response = client.get(LIVE_MENU_URL)
        check(response.status.isSuccess()) { "starbucks.com answered ${response.status}" }
        val parsed = json.decodeFromString(MenuResponse.serializer(), response.bodyAsText())
        check(parsed.menus.isNotEmpty()) { "starbucks.com returned an empty menu" }
        return MenuCatalog.from(parsed, fetchedAt = null, sourceUrl = LIVE_MENU_URL)
    }

    companion object {
        const val LIVE_MENU_URL = "https://www.starbucks.com/apiproxy/v1/ordering/menu"

        val lenientJson: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        fun defaultHttpClient(): HttpClient = HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
            }
            defaultRequest {
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, "kmatch-menu-search-sample/1.0 (+https://github.com/likhithsj/kmatch)")
            }
        }
    }
}
