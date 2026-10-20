# Starbucks menu search — Android + iOS

A Compose Multiplatform app whose search box is [kmatch](../../). It pulls the
Starbucks® menu from the web (the same JSON the starbucks.com menu page
loads), bundles a snapshot for instant/offline start, and searches it with
typo tolerance, accent folding, nicknames and a soft category boost — all
from one `extractTop` call.

```
pumkin spice  →  Pumpkin Spice Latte        (typo)
caffe latte   →  Caffè Latte                (accent)
frap          →  …Frappuccino® Blended…     (nickname, via alias table)
capucino      →  Cappuccino                 (typo)
```

Every row shows kmatch's text score (RapidFuzz-identical, 0–100) and, when a
category boost moved it, the boosted rank score — the point of the sample is
to see the library work.

## Run it

**Android** (Linux/macOS/Windows; needs the Android SDK and `ANDROID_HOME`):

```bash
cd samples/starbucks-menu
../../gradlew -p . :composeApp:assembleDebug       # APK in composeApp/build/outputs/apk/debug/
../../gradlew -p . :composeApp:installDebug        # onto a connected device/emulator
../../gradlew -p . :composeApp:testDebugUnitTest   # shared + snapshot tests
```

**iOS** (macOS with Xcode 15+):

```bash
open samples/starbucks-menu/iosApp/iosApp.xcodeproj
```

Pick a simulator and run. The "Compile Kotlin Framework" build phase calls
Gradle (`:composeApp:embedAndSignAppleFrameworkForXcode`), so the first build
is slow. To run on a device, put your Apple team id in
`iosApp/Configuration/Config.xcconfig` (`TEAM_ID=`).

The sample reuses the repository root's Gradle wrapper like every other
sample, and depends on kmatch **from source** via a composite build
(`includeBuild("../..")` in `settings.gradle.kts`) so it always exercises the
current tree — including `searchFold`, which shipped in 1.0.0. Once that
version is on Maven Central, dropping the `includeBuild` block turns the
dependency into the ordinary `io.github.likhithsj:kmatch:1.0.0` line.

## Where the menu comes from

| | |
|---|---|
| Live | `GET https://www.starbucks.com/apiproxy/v1/ordering/menu` on every launch and on the refresh button |
| Bundled | `composeApp/src/commonMain/composeResources/files/menu.json`, refreshed with `python3 tools/fetch_menu.py` |

The endpoint returns the category tree (9 top-level menus, ~340 product
entries, ~290 distinct products) with names, images, sizes, hot/iced form and
the product's web path. It does **not** carry prices, calories or
descriptions — those are per-store and per-product calls behind the ordering
flow — so the app shows what the public menu shows and links each item to its
starbucks.com page. The status line under the search field says which source
is on screen (`Live menu from starbucks.com` / `Bundled snapshot from Oct 20,
2026` / `Offline · showing snapshot…`).

The same product appears under several categories on the site ("Trending"
repeats drinks that also live under "Latte"); `MenuCatalog` collapses them to
one item per product+form that remembers every path it was found under, so
results never show duplicate rows and the detail sheet lists "Found under".

## The kmatch part

All of it is [`MenuSearchEngine.kt`](composeApp/src/commonMain/kotlin/io/github/likhithsj/menusearch/search/MenuSearchEngine.kt),
and it follows [docs/learn/07](../../docs/learn/07-categories-and-priorities.md):

```kotlin
// Each item contributes a few SHORT keys: its name, name + category word when
// the name lacks it, and each nickname on its own ("frap", "psl", "shaken").
val scored =
    extractAll(query, nameKeys,  keySelector = { it.text }, processor = ::searchFold, scoreCutoff = 55.0) +
    extractAll(query, aliasKeys, keySelector = { it.text }, processor = ::searchFold, scoreCutoff = 55.0,
               scorer = Scorers.TokenSetRatio)   // nicknames match as whole words
// item score = best (score × key weight) over its keys; then × category boost
```

Boosts live in the 0.9–1.0 band (featured "The Latest" 1.0, beverages 0.97,
food 0.95, at-home coffee 0.90): a featured drink wins a *tie* with its
everyday twin and never beats a clearly better text match. Highlights come
from `matchingRanges`, run on lowercased text so the ranges index straight
into the displayed name.

Three things learned on the real menu, each encoded in a test:

- **Score nicknames as their own keys, not glued onto the name.** One long
  "name + category + aliases" string put `frap` against 60-character keys,
  tripped WRatio's length-ratio cliff (partial scale 0.9 → 0.6, score 60) and
  let *Poppi® Grape Soda* (3 of 4 letters, short name, score 67) outrank every
  Frappuccino. As a 4-character key of its own, `frap` scores 100.
- **Add the category word only when the name lacks it, and never from a
  shelf.** "Cappuccino" under "Cappuccino" gains nothing; "Pike Place® Roast"
  under "Medium Roast" becomes findable by `medium roast`; "Trending" is where
  an item is displayed, not what it is.
- **A category word is a weaker description than the name.** `americano`
  scores 90 for both *Caffè Americano* and *Aerocano* (category "Iced
  Americano"); the category key's 0.95 weight puts the real Americano first,
  and the bare-name score breaks any remaining tie.
- **Nicknames match as whole words.** Under WRatio a 3-letter key like `psl`
  lights up inside `plumbing`; scoring alias keys with `TokenSetRatio` keeps
  them exact-word (typo-tolerant: `psll` still works) while names keep the
  partial-match behaviour that lets `mocha` find *Caffè Mocha*.

## Layout

```
composeApp/src/commonMain/kotlin/io/github/likhithsj/menusearch/
  menu/MenuModels.kt         wire DTOs + flattened MenuItem / BrowseNode / MenuCatalog
  menu/MenuRepository.kt     bundled snapshot + live fetch (Ktor)
  search/MenuSearchEngine.kt the kmatch integration (this is the sample)
  ui/AppState.kt             state holder: data source, query, selection
  ui/App.kt                  Material 3 UI: search, browse, detail sheet
composeApp/src/androidMain   MainActivity + manifest
composeApp/src/iosMain       MainViewController (Compose → UIKit)
iosApp/                      Xcode host project (SwiftUI shell)
tools/fetch_menu.py          refresh the bundled snapshot
```

Tests: `commonTest` covers flatten/dedupe and the ranking rules on a fixture;
`androidUnitTest/BundledSnapshotTest` runs real queries against the real
bundled snapshot so a payload-shape change or ranking regression fails
locally first.

## Trademarks

Starbucks and Frappuccino are trademarks of Starbucks Corporation. This is an
unaffiliated open-source demo of a search library; it uses the public menu
data and no Starbucks branding or logos.
