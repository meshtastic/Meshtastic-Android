/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.DatabaseConstants
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.navigation.WifiProvisionRoute
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.acknowledgements
import org.meshtastic.core.resources.app_settings
import org.meshtastic.core.resources.app_version
import org.meshtastic.core.resources.bottom_nav_settings
import org.meshtastic.core.resources.contrast
import org.meshtastic.core.resources.device_db_cache_limit
import org.meshtastic.core.resources.device_db_cache_limit_summary
import org.meshtastic.core.resources.info
import org.meshtastic.core.resources.modules_already_unlocked
import org.meshtastic.core.resources.modules_unlocked
import org.meshtastic.core.resources.preferences_language
import org.meshtastic.core.resources.remotely_administrating
import org.meshtastic.core.resources.theme
import org.meshtastic.core.resources.wifi_devices
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.icon.ChevronRight
import org.meshtastic.core.ui.icon.FormatPaint
import org.meshtastic.core.ui.icon.Info
import org.meshtastic.core.ui.icon.Language
import org.meshtastic.core.ui.icon.Memory
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Wifi
import org.meshtastic.core.ui.util.rememberShowToastResource
import org.meshtastic.feature.settings.component.ContrastPickerDialog
import org.meshtastic.feature.settings.component.ExpressiveSection
import org.meshtastic.feature.settings.component.HomoglyphSetting
import org.meshtastic.feature.settings.component.NotificationSection
import org.meshtastic.feature.settings.component.ThemePickerDialog
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.ModuleRoute
import org.meshtastic.feature.settings.radio.RadioConfigItemList
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import kotlin.time.Duration.Companion.seconds

/**
 * Desktop-specific top-level settings screen. Replaces the Android `SettingsScreen` which uses Android-specific APIs
 * (Activity, permissions, etc.).
 *
 * Shows radio configuration entry points that are fully shared in commonMain, plus app-level settings (theme,
 * homoglyph, DB cache limit) and an App Info section (About link, version easter egg).
 */
@Suppress("LongMethod")
@Composable
fun DesktopSettingsScreen(
    radioConfigViewModel: RadioConfigViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigate: (Route) -> Unit,
) {
    val state by radioConfigViewModel.radioConfigState.collectAsStateWithLifecycle()
    val destNode by radioConfigViewModel.destNode.collectAsStateWithLifecycle()
    val localConfig by settingsViewModel.localConfig.collectAsStateWithLifecycle()
    val homoglyphEnabled by radioConfigViewModel.homoglyphEncodingEnabledFlow.collectAsStateWithLifecycle(false)
    val excludedModulesUnlocked by settingsViewModel.excludedModulesUnlocked.collectAsStateWithLifecycle()
    val cacheLimit by settingsViewModel.dbCacheLimit.collectAsStateWithLifecycle()
    val isOtaCapable by settingsViewModel.isOtaCapable.collectAsStateWithLifecycle()

    var showThemePickerDialog by remember { mutableStateOf(false) }
    var showLanguagePickerDialog by remember { mutableStateOf(false) }
    var showContrastPickerDialog by remember { mutableStateOf(false) }
    if (showThemePickerDialog) {
        ThemePickerDialog(
            onClickTheme = { settingsViewModel.setTheme(it) },
            onDismiss = { showThemePickerDialog = false },
        )
    }

    if (showContrastPickerDialog) {
        ContrastPickerDialog(
            onClickContrast = { settingsViewModel.setContrastLevel(it) },
            onDismiss = { showContrastPickerDialog = false },
        )
    }

    if (showLanguagePickerDialog) {
        LanguagePickerDialog(
            onSelectLanguage = { tag -> settingsViewModel.setLocale(tag) },
            onDismiss = { showLanguagePickerDialog = false },
        )
    }

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.bottom_nav_settings),
                subtitle =
                if (state.isLocal) {
                    null
                } else {
                    val remoteName = destNode?.user?.long_name ?: ""
                    stringResource(Res.string.remotely_administrating, remoteName)
                },
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = false,
                onNavigateUp = {},
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RadioConfigItemList(
                state = state,
                isManaged = localConfig.security?.is_managed ?: false,
                isOtaCapable = isOtaCapable,
                onRouteClick = { route ->
                    val navRoute =
                        when (route) {
                            is ConfigRoute -> route.route
                            is ModuleRoute -> route.route
                            else -> null
                        }
                    navRoute?.let { onNavigate(it) }
                },
                onNavigate = onNavigate,
                onImport = {
                    // Profile import not yet supported on Desktop
                },
                onExport = {
                    // Profile export not yet supported on Desktop
                },
            )

            // App-local settings are only relevant when configuring the local node
            if (state.isLocal) {
                ExpressiveSection(title = stringResource(Res.string.app_settings)) {
                    ListItem(
                        text = stringResource(Res.string.theme),
                        leadingIcon = MeshtasticIcons.FormatPaint,
                        trailingIcon = null,
                    ) {
                        showThemePickerDialog = true
                    }

                    ListItem(
                        text = stringResource(Res.string.contrast),
                        leadingIcon = MeshtasticIcons.FormatPaint,
                        trailingIcon = null,
                    ) {
                        showContrastPickerDialog = true
                    }

                    ListItem(
                        text = stringResource(Res.string.preferences_language),
                        leadingIcon = MeshtasticIcons.Language,
                        trailingIcon = null,
                    ) {
                        showLanguagePickerDialog = true
                    }

                    HomoglyphSetting(
                        homoglyphEncodingEnabled = homoglyphEnabled,
                        onToggle = { radioConfigViewModel.toggleHomoglyphCharactersEncodingEnabled() },
                    )

                    val cacheItems = remember {
                        (DatabaseConstants.MIN_CACHE_LIMIT..DatabaseConstants.MAX_CACHE_LIMIT).map {
                            it.toLong() to it.toString()
                        }
                    }
                    DropDownPreference(
                        title = stringResource(Res.string.device_db_cache_limit),
                        enabled = true,
                        items = cacheItems,
                        selectedItem = cacheLimit.toLong(),
                        onItemSelected = { selected -> settingsViewModel.setDbCacheLimit(selected.toInt()) },
                        summary = stringResource(Res.string.device_db_cache_limit_summary),
                    )
                }

                ExpressiveSection(title = stringResource(Res.string.wifi_devices)) {
                    ListItem(text = stringResource(Res.string.wifi_devices), leadingIcon = MeshtasticIcons.Wifi) {
                        onNavigate(WifiProvisionRoute.WifiProvision())
                    }
                }

                NotificationSection(
                    messagesEnabled = settingsViewModel.messagesEnabled.collectAsStateWithLifecycle().value,
                    onToggleMessages = { settingsViewModel.setMessagesEnabled(it) },
                    nodeEventsEnabled = settingsViewModel.nodeEventsEnabled.collectAsStateWithLifecycle().value,
                    onToggleNodeEvents = { settingsViewModel.setNodeEventsEnabled(it) },
                    lowBatteryEnabled = settingsViewModel.lowBatteryEnabled.collectAsStateWithLifecycle().value,
                    onToggleLowBattery = { settingsViewModel.setLowBatteryEnabled(it) },
                )

                DesktopAppInfoSection(
                    appVersionName = settingsViewModel.appVersionName,
                    excludedModulesUnlocked = excludedModulesUnlocked,
                    onUnlockExcludedModules = { settingsViewModel.unlockExcludedModules() },
                    onNavigateToAbout = { onNavigate(SettingsRoute.About) },
                )
            }
        }
    }
}

/** Desktop App Info section: About link and version with excluded-modules unlock easter egg. */
@Composable
private fun DesktopAppInfoSection(
    appVersionName: String,
    excludedModulesUnlocked: Boolean,
    onUnlockExcludedModules: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    ExpressiveSection(title = stringResource(Res.string.info)) {
        ListItem(
            text = stringResource(Res.string.acknowledgements),
            leadingIcon = MeshtasticIcons.Info,
            trailingIcon = MeshtasticIcons.ChevronRight,
        ) {
            onNavigateToAbout()
        }

        DesktopAppVersionButton(
            excludedModulesUnlocked = excludedModulesUnlocked,
            appVersionName = appVersionName,
            onUnlockExcludedModules = onUnlockExcludedModules,
        )
    }
}

private const val UNLOCK_CLICK_COUNT = 5
private const val UNLOCKED_CLICK_COUNT = 3
private const val UNLOCK_TIMEOUT_SECONDS = 1

@Composable
private fun DesktopAppVersionButton(
    excludedModulesUnlocked: Boolean,
    appVersionName: String,
    onUnlockExcludedModules: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val showToast = rememberShowToastResource()
    var clickCount by remember { mutableStateOf(0) }

    LaunchedEffect(clickCount) {
        if (clickCount in 1..<UNLOCK_CLICK_COUNT) {
            delay(UNLOCK_TIMEOUT_SECONDS.seconds)
            clickCount = 0
        }
    }

    ListItem(
        text = stringResource(Res.string.app_version),
        leadingIcon = MeshtasticIcons.Memory,
        supportingText = appVersionName,
        trailingIcon = null,
    ) {
        clickCount = clickCount.inc().coerceIn(0, UNLOCK_CLICK_COUNT)

        when {
            clickCount == UNLOCKED_CLICK_COUNT && excludedModulesUnlocked -> {
                clickCount = 0
                scope.launch { showToast(Res.string.modules_already_unlocked) }
            }

            clickCount == UNLOCK_CLICK_COUNT -> {
                clickCount = 0
                onUnlockExcludedModules()
                scope.launch { showToast(Res.string.modules_unlocked) }
            }
        }
    }
}

/**
 * Supported languages — tag must match the CMP `values-<qualifier>` directory names. Empty tag means system default.
 * Display names are written in the native language for clarity.
 */
private val SUPPORTED_LANGUAGES =
    listOf(
        "" to "System default",
        "ar" to "العربية",
        "be" to "Беларуская",
        "bg" to "Български",
        "ca" to "Català",
        "cs" to "Čeština",
        "de" to "Deutsch",
        "el" to "Ελληνικά",
        "en" to "English",
        "es" to "Español",
        "et" to "Eesti",
        "fi" to "Suomi",
        "fr" to "Français",
        "ga" to "Gaeilge",
        "gl" to "Galego",
        "he" to "עברית",
        "hr" to "Hrvatski",
        "ht" to "Kreyòl Ayisyen",
        "hu" to "Magyar",
        "is" to "Íslenska",
        "it" to "Italiano",
        "ja" to "日本語",
        "ko" to "한국어",
        "lt" to "Lietuvių",
        "nl" to "Nederlands",
        "no" to "Norsk",
        "pl" to "Polski",
        "pt" to "Português",
        "pt-BR" to "Português (Brasil)",
        "ro" to "Română",
        "ru" to "Русский",
        "sk" to "Slovenčina",
        "sl" to "Slovenščina",
        "sq" to "Shqip",
        "sr" to "Српски",
        "sv" to "Svenska",
        "tr" to "Türkçe",
        "uk" to "Українська",
        "zh-CN" to "中文 (简体)",
        "zh-TW" to "中文 (繁體)",
    )

@Composable
private fun LanguagePickerDialog(onSelectLanguage: (String) -> Unit, onDismiss: () -> Unit) {
    MeshtasticDialog(
        title = stringResource(Res.string.preferences_language),
        onDismiss = onDismiss,
        text = {
            LazyColumn {
                items(SUPPORTED_LANGUAGES) { (tag, displayName) ->
                    ListItem(text = displayName, trailingIcon = null) {
                        onSelectLanguage(tag)
                        onDismiss()
                    }
                }
            }
        },
    )
}
