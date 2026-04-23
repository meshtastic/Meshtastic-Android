/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eygraber.uri.toKmpUri
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.toDate
import org.meshtastic.core.common.util.toInstant
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.navigation.WifiProvisionRoute
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bottom_nav_settings
import org.meshtastic.core.resources.export_configuration
import org.meshtastic.core.resources.filter_settings
import org.meshtastic.core.resources.import_configuration
import org.meshtastic.core.resources.preferences_language
import org.meshtastic.core.resources.remotely_administrating
import org.meshtastic.core.resources.wifi_devices
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.icon.FilterList
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Wifi
import org.meshtastic.feature.settings.component.AppInfoSection
import org.meshtastic.feature.settings.component.AppearanceSection
import org.meshtastic.feature.settings.component.ContrastPickerDialog
import org.meshtastic.feature.settings.component.ExpressiveSection
import org.meshtastic.feature.settings.component.PersistenceSection
import org.meshtastic.feature.settings.component.PrivacySection
import org.meshtastic.feature.settings.component.ThemePickerDialog
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.ModuleRoute
import org.meshtastic.feature.settings.radio.RadioConfigItemList
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.component.EditDeviceProfileDialog
import org.meshtastic.feature.settings.util.LanguageUtils
import org.meshtastic.feature.settings.util.LanguageUtils.languageMap
import org.meshtastic.proto.DeviceProfile
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    viewModel: RadioConfigViewModel,
    onClickNodeChip: (Int) -> Unit = {},
    onNavigate: (Route) -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val excludedModulesUnlocked by settingsViewModel.excludedModulesUnlocked.collectAsStateWithLifecycle()
    val localConfig by settingsViewModel.localConfig.collectAsStateWithLifecycle()
    val ourNode by settingsViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val isConnected by settingsViewModel.isConnected.collectAsStateWithLifecycle(false)
    val isOtaCapable by settingsViewModel.isOtaCapable.collectAsStateWithLifecycle()
    val destNode by viewModel.destNode.collectAsStateWithLifecycle()
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    var deviceProfile by remember { mutableStateOf<DeviceProfile?>(null) }
    var showEditDeviceProfileDialog by remember { mutableStateOf(false) }

    val importConfigLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                showEditDeviceProfileDialog = true
                it.data?.data?.let { uri ->
                    viewModel.importProfile(uri.toKmpUri()) { profile -> deviceProfile = profile }
                }
            }
        }

    val exportConfigLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                it.data?.data?.let { uri -> viewModel.exportProfile(uri.toKmpUri(), deviceProfile!!) }
            }
        }

    if (showEditDeviceProfileDialog) {
        EditDeviceProfileDialog(
            title =
            if (deviceProfile != null) {
                stringResource(Res.string.import_configuration)
            } else {
                stringResource(Res.string.export_configuration)
            },
            deviceProfile = deviceProfile ?: viewModel.currentDeviceProfile,
            onConfirm = {
                showEditDeviceProfileDialog = false
                if (deviceProfile != null) {
                    viewModel.installProfile(it)
                } else {
                    deviceProfile = it
                    val nodeName = (it.short_name ?: "").ifBlank { "node" }
                    val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                    val dateStr = dateFormat.format(nowMillis.toInstant().toDate())
                    val fileName = "Meshtastic_${nodeName}_${dateStr}_nodeConfig.cfg"
                    val intent =
                        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/*"
                            putExtra(Intent.EXTRA_TITLE, fileName)
                        }
                    exportConfigLauncher.launch(intent)
                }
            },
            onDismiss = {
                showEditDeviceProfileDialog = false
                deviceProfile = null
            },
        )
    }

    var showLanguagePickerDialog by rememberSaveable { mutableStateOf(false) }
    if (showLanguagePickerDialog) {
        LanguagePickerDialog { showLanguagePickerDialog = false }
    }

    var showThemePickerDialog by rememberSaveable { mutableStateOf(false) }
    if (showThemePickerDialog) {
        ThemePickerDialog(
            onClickTheme = { settingsViewModel.setTheme(it) },
            onDismiss = { showThemePickerDialog = false },
        )
    }

    var showContrastPickerDialog by remember { mutableStateOf(false) }
    if (showContrastPickerDialog) {
        ContrastPickerDialog(
            onClickContrast = { settingsViewModel.setContrastLevel(it) },
            onDismiss = { showContrastPickerDialog = false },
        )
    }

    Scaffold(
        topBar = {
            // Show back arrow when remotely administering (caller supplies onBack and we're not on the local node).
            val showBack = onBack != null && !state.isLocal
            MainAppBar(
                title = stringResource(Res.string.bottom_nav_settings),
                subtitle =
                if (state.isLocal) {
                    ourNode?.user?.long_name
                } else {
                    val remoteName = destNode?.user?.long_name ?: ""
                    stringResource(Res.string.remotely_administrating, remoteName)
                },
                ourNode = ourNode,
                showNodeChip = ourNode != null && isConnected && state.isLocal,
                canNavigateUp = showBack,
                onNavigateUp = { onBack?.invoke() },
                actions = {},
                onClickChip = { node -> onClickNodeChip(node.num) },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(paddingValues).padding(16.dp),
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
                onImport = {
                    viewModel.clearPacketResponse()
                    deviceProfile = null
                    val intent =
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/*"
                        }
                    importConfigLauncher.launch(intent)
                },
                onExport = {
                    viewModel.clearPacketResponse()
                    deviceProfile = null
                    showEditDeviceProfileDialog = true
                },
                onNavigate = onNavigate,
            )

            // App-local settings are only relevant when configuring the local node
            if (state.isLocal) {
                PrivacySection(
                    analyticsAvailable = state.analyticsAvailable,
                    analyticsEnabled = viewModel.analyticsAllowedFlow.collectAsStateWithLifecycle(true).value,
                    onToggleAnalytics = { viewModel.toggleAnalyticsAllowed() },
                    provideLocation = settingsViewModel.provideLocation.collectAsStateWithLifecycle().value,
                    onToggleLocation = { settingsViewModel.setProvideLocation(it) },
                    homoglyphEnabled = viewModel.homoglyphEncodingEnabledFlow.collectAsStateWithLifecycle(false).value,
                    onToggleHomoglyph = { viewModel.toggleHomoglyphCharactersEncodingEnabled() },
                    startProvideLocation = { settingsViewModel.startProvidingLocation() },
                    stopProvideLocation = { settingsViewModel.stopProvidingLocation() },
                )

                AppearanceSection(
                    onShowLanguagePicker = { showLanguagePickerDialog = true },
                    onShowThemePicker = { showThemePickerDialog = true },
                    onShowContrastPicker = { showContrastPickerDialog = true },
                )

                ExpressiveSection(title = stringResource(Res.string.wifi_devices)) {
                    ListItem(text = stringResource(Res.string.wifi_devices), leadingIcon = MeshtasticIcons.Wifi) {
                        onNavigate(WifiProvisionRoute.WifiProvision())
                    }
                }

                ExpressiveSection(title = stringResource(Res.string.filter_settings)) {
                    ListItem(
                        text = stringResource(Res.string.filter_settings),
                        leadingIcon = MeshtasticIcons.FilterList,
                    ) {
                        onNavigate(SettingsRoute.FilterSettings)
                    }
                }

                PersistenceSection(
                    cacheLimit = settingsViewModel.dbCacheLimit.collectAsStateWithLifecycle().value,
                    onSetCacheLimit = { settingsViewModel.setDbCacheLimit(it) },
                    nodeShortName = ourNode?.user?.short_name ?: "",
                    onExportData = { settingsViewModel.saveDataCsv(it.toKmpUri()) },
                )

                AppInfoSection(
                    appVersionName = settingsViewModel.appVersionName,
                    excludedModulesUnlocked = excludedModulesUnlocked,
                    onUnlockExcludedModules = { settingsViewModel.unlockExcludedModules() },
                    onShowAppIntro = { settingsViewModel.showAppIntro() },
                    onNavigateToAbout = { onNavigate(SettingsRoute.About) },
                )
            }
        }
    }
}

@Composable
private fun LanguagePickerDialog(onDismiss: () -> Unit) {
    MeshtasticDialog(
        title = stringResource(Res.string.preferences_language),
        onDismiss = onDismiss,
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                languageMap().forEach { (languageTag, languageName) ->
                    ListItem(text = languageName, trailingIcon = null) {
                        LanguageUtils.setAppLocale(languageTag)
                        onDismiss()
                    }
                }
            }
        },
    )
}
