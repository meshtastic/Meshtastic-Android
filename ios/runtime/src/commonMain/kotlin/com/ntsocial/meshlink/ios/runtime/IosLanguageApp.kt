/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Meshtastic Android-derived portions, where present:
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.ntsocial.meshlink.ios.runtime

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalProvidableLocaleList
import androidx.compose.ui.text.intl.LocaleList
import com.ntsocial.meshlink.core.repository.UiPrefs
import com.ntsocial.meshlink.core.ui.theme.AppTheme
import com.ntsocial.meshlink.core.ui.viewmodel.UIViewModel
import com.ntsocial.meshlink.feature.intro.LanguageSelectScreen
import com.ntsocial.meshlink.feature.intro.requiresInitialLanguageSelection
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** iOS root that owns first-launch language selection and the live Compose resource locale. */
@Composable
internal fun IosShellApp(controller: IosShellController) {
    val uiPrefs = koinInject<UiPrefs>()
    val launchPreferences by uiPrefs.appLaunchPreferences.collectAsState()
    val uiViewModel = koinViewModel<UIViewModel>()
    val theme by uiViewModel.theme.collectAsState()
    val darkTheme =
        when (theme) {
            THEME_LIGHT -> false
            THEME_DARK -> true
            else -> isSystemInDarkTheme()
        }
    val preferences = launchPreferences
    IosAppLocale(languageTag = preferences?.locale.orEmpty()) {
        AppTheme(darkTheme = darkTheme, dynamicColor = false) {
            when {
                preferences == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))

                requiresInitialLanguageSelection(
                    appIntroCompleted = preferences.appIntroCompleted,
                    persistedLocale = preferences.locale,
                ) -> IosInitialLanguageSelection(uiPrefs = uiPrefs)

                else -> IosMainApp(controller = controller, uiViewModel = uiViewModel)
            }
        }
    }
}

@Composable
@Suppress("RestrictedApi")
private fun IosAppLocale(languageTag: String, content: @Composable () -> Unit) {
    val inheritedLocaleList = LocalProvidableLocaleList.current
    val localeList =
        remember(languageTag, inheritedLocaleList) {
            if (languageTag.isBlank()) {
                inheritedLocaleList
            } else {
                applyIosResourceLanguage(languageTag)
                LocaleList(languageTag)
            }
        }
    CompositionLocalProvider(LocalProvidableLocaleList provides localeList) { key(languageTag) { content() } }
}

/** Applies the app-specific locale read by Compose Resources before composing localized content. */
internal expect fun applyIosResourceLanguage(languageTag: String)

@Composable
private fun IosInitialLanguageSelection(uiPrefs: UiPrefs) {
    val coroutineScope = rememberCoroutineScope()
    var applyingLanguage by remember { mutableStateOf(false) }
    LanguageSelectScreen(
        currentTag = uiPrefs.appLaunchPreferences.value?.locale.orEmpty(),
        onSelect = { languageTag ->
            if (!applyingLanguage && languageTag in IOS_LANGUAGE_TAGS) {
                applyingLanguage = true
                coroutineScope.launch {
                    try {
                        uiPrefs.setLocaleAndAwait(languageTag)
                    } finally {
                        applyingLanguage = false
                    }
                }
            }
        },
    )
}

private const val THEME_LIGHT = 1
private const val THEME_DARK = 2
private val IOS_LANGUAGE_TAGS = setOf("en", "zh-TW", "ja")
