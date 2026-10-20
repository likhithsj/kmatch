package io.github.likhithsj.menusearch

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import io.github.likhithsj.menusearch.ui.App
import platform.UIKit.UIViewController

/** Entry point the Swift host embeds (see iosApp/iosApp/ContentView.swift). */
@Suppress("unused", "FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController { AppRoot() }

@Composable
private fun AppRoot() {
    App(isDark = isSystemInDarkTheme())
}
