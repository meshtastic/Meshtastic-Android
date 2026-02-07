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

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.Settings.ACTION_APP_LOCALE_SETTINGS
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.rounded.AppSettingsAlt
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Output
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.gpsDisabled
import org.meshtastic.core.database.DatabaseConstants
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.acknowledgements
import org.meshtastic.core.strings.analytics_okay
import org.meshtastic.core.strings.app_settings
import org.meshtastic.core.strings.app_version
import org.meshtastic.core.strings.bottom_nav_settings
import org.meshtastic.core.strings.choose_theme
import org.meshtastic.core.strings.device_db_cache_limit
import org.meshtastic.core.strings.device_db_cache_limit_summary
import org.meshtastic.core.strings.dynamic
import org.meshtastic.core.strings.export_configuration
import org.meshtastic.core.strings.export_data_csv
import org.meshtastic.core.strings.import_configuration
import org.meshtastic.core.strings.intro_show
import org.meshtastic.core.strings.location_disabled
import org.meshtastic.core.strings.modules_already_unlocked
import org.meshtastic.core.strings.modules_unlocked
import org.meshtastic.core.strings.preferences_language
import org.meshtastic.core.strings.provide_location_to_mesh
import org.meshtastic.core.strings.remotely_administrating
import org.meshtastic.core.strings.save_rangetest
import org.meshtastic.core.strings.system_settings
import org.meshtastic.core.strings.theme
import org.meshtastic.core.strings.theme_dark
import org.meshtastic.core.strings.theme_light
import org.meshtastic.core.strings.theme_system
import org.meshtastic.core.strings.use_homoglyph_characters_encoding
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.SwitchListItem
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.core.ui.theme.MODE_DYNAMIC
import org.meshtastic.core.ui.util.showToast
import org.meshtastic.feature.settings.navigation.getNavRouteFrom
import org.meshtastic.feature.settings.radio.RadioConfigItemList
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.component.EditDeviceProfileDialog
import org.meshtastic.feature.settings.radio.component.PacketResponseStateDialog
import org.meshtastic.feature.settings.util.LanguageUtils
import org.meshtastic.feature.settings.util.LanguageUtils.languageMap
import org.meshtastic.proto.DeviceProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalPermissionsApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    viewModel: RadioConfigViewModel = hiltViewModel(),
    onClickNodeChip: (Int) -> Unit = {},
    onNavigate: (Route) -> Unit = {},
) {
    val excludedModulesUnlocked by settingsViewModel.excludedModulesUnlocked.collectAsStateWithLifecycle()
    val localConfig by settingsViewModel.localConfig.collectAsStateWithLifecycle()
    val ourNode by settingsViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val isConnected by settingsViewModel.isConnected.collectAsStateWithLifecycle(false)
    val isOtaCapable by settingsViewModel.isOtaCapable.collectAsStateWithLifecycle()
    val destNode by viewModel.destNode.collectAsStateWithLifecycle()
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    var isWaiting by remember { mutableStateOf(false) }
    if (isWaiting) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = {
                isWaiting = false
                viewModel.clearPacketResponse()
            },
            onComplete = {
                getNavRouteFrom(state.route)?.let { route ->
                    isWaiting = false
                    viewModel.clearPacketResponse()
                    onNavigate(route)
                }
            },
        )
    }

    var deviceProfile by remember { mutableStateOf<DeviceProfile?>(null) }
    var showEditDeviceProfileDialog by remember { mutableStateOf(false) }

    val importConfigLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                showEditDeviceProfileDialog = true
                it.data?.data?.let { uri -> viewModel.importProfile(uri) { profile -> deviceProfile = profile } }
            }
        }

    val exportConfigLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                it.data?.data?.let { uri -> viewModel.exportProfile(uri, deviceProfile!!) }
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
                    val dateFormat = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
                    val dateStr = dateFormat.format(java.util.Date())
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

    var showLanguagePickerDialog by remember { mutableStateOf(false) }
    if (showLanguagePickerDialog) {
        LanguagePickerDialog { showLanguagePickerDialog = false }
    }

    var showThemePickerDialog by remember { mutableStateOf(false) }
    if (showThemePickerDialog) {
        ThemePickerDialog(
            onClickTheme = { settingsViewModel.setTheme(it) },
            onDismiss = { showThemePickerDialog = false },
        )
    }

    Scaffold(
        topBar = {
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
                canNavigateUp = false,
                onNavigateUp = {},
                actions = {},
                onClickChip = { node -> onClickNodeChip(node.num) },
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(paddingValues).padding(16.dp)) {
            RadioConfigItemList(
                state = state,
                isManaged = localConfig.security?.is_managed ?: false,
                node = destNode,
                excludedModulesUnlocked = excludedModulesUnlocked,
                isOtaCapable = isOtaCapable,
                onPreserveFavoritesToggle = { viewModel.setPreserveFavorites(it) },
                onRouteClick = { route ->
                    isWaiting = true
                    viewModel.setResponseStateLoading(route)
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

            val context = LocalContext.current

            TitledCard(title = stringResource(Res.string.app_settings), modifier = Modifier.padding(top = 16.dp)) {
                if (state.analyticsAvailable) {
                    val allowed by viewModel.analyticsAllowedFlow.collectAsStateWithLifecycle(false)
                    SwitchListItem(
                        text = stringResource(Res.string.analytics_okay),
                        checked = allowed,
                        leadingIcon = Icons.Default.BugReport,
                        onClick = { viewModel.toggleAnalyticsAllowed() },
                    )
                }

                val locationPermissionsState =
                    rememberMultiplePermissionsState(permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION))
                val isGpsDisabled = context.gpsDisabled()
                val provideLocation by settingsViewModel.provideLocation.collectAsStateWithLifecycle()

                LaunchedEffect(provideLocation, locationPermissionsState.allPermissionsGranted, isGpsDisabled) {
                    if (provideLocation) {
                        if (locationPermissionsState.allPermissionsGranted) {
                            if (!isGpsDisabled) {
                                settingsViewModel.meshService?.startProvideLocation()
                            } else {
                                context.showToast(Res.string.location_disabled)
                            }
                        } else {
                            // Request permissions if not granted and user wants to provide location
                            locationPermissionsState.launchMultiplePermissionRequest()
                        }
                    } else {
                        settingsViewModel.meshService?.stopProvideLocation()
                    }
                }

                SwitchListItem(
                    text = stringResource(Res.string.provide_location_to_mesh),
                    leadingIcon = Icons.Rounded.LocationOn,
                    enabled = !isGpsDisabled,
                    checked = provideLocation,
                    onClick = { settingsViewModel.setProvideLocation(!provideLocation) },
                )

                val homoglyphEncodingEnabled by
                    viewModel.homoglyphEncodingEnabledFlow.collectAsStateWithLifecycle(false)
                SwitchListItem(
                    text = stringResource(Res.string.use_homoglyph_characters_encoding),
                    checked = homoglyphEncodingEnabled,
                    leadingIcon = Icons.Default.Abc,
                    onClick = { viewModel.toggleHomoglyphCharactersEncodingEnabled() },
                )

                val settingsLauncher =
                    rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {}

                // On Android 12 and below, system app settings for language are not available. Use the in-app language
                // picker for these devices.
                val useInAppLangPicker = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                ListItem(
                    text = stringResource(Res.string.preferences_language),
                    leadingIcon = Icons.Rounded.Language,
                    trailingIcon = if (useInAppLangPicker) null else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                ) {
                    if (useInAppLangPicker) {
                        showLanguagePickerDialog = true
                    } else {
                        val intent = Intent(ACTION_APP_LOCALE_SETTINGS, "package:${context.packageName}".toUri())
                        if (intent.resolveActivity(context.packageManager) != null) {
                            settingsLauncher.launch(intent)
                        } else {
                            // Fall back to the in-app picker
                            showLanguagePickerDialog = true
                        }
                    }
                }

                ListItem(
                    text = stringResource(Res.string.theme),
                    leadingIcon = Icons.Rounded.FormatPaint,
                    trailingIcon = null,
                ) {
                    showThemePickerDialog = true
                }

                // Node DB cache limit (App setting)
                val cacheLimit = settingsViewModel.dbCacheLimit.collectAsStateWithLifecycle().value
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

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val nodeName = ourNode?.user?.short_name ?: ""

                val exportRangeTestLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        if (it.resultCode == RESULT_OK) {
                            it.data?.data?.let { uri -> settingsViewModel.saveDataCsv(uri) }
                        }
                    }
                ListItem(
                    text = stringResource(Res.string.save_rangetest),
                    leadingIcon = Icons.Rounded.Output,
                    trailingIcon = null,
                ) {
                    val intent =
                        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/csv"
                            putExtra(Intent.EXTRA_TITLE, "Meshtastic_rangetest_${nodeName}_$timestamp.csv")
                        }
                    exportRangeTestLauncher.launch(intent)
                }

                val exportDataLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        if (it.resultCode == RESULT_OK) {
                            it.data?.data?.let { uri -> settingsViewModel.saveDataCsv(uri) }
                        }
                    }
                ListItem(
                    text = stringResource(Res.string.export_data_csv),
                    leadingIcon = Icons.Rounded.Output,
                    trailingIcon = null,
                ) {
                    val intent =
                        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/csv"
                            putExtra(Intent.EXTRA_TITLE, "Meshtastic_datalog_${nodeName}_$timestamp.csv")
                        }
                    exportDataLauncher.launch(intent)
                }

                ListItem(
                    text = stringResource(Res.string.intro_show),
                    leadingIcon = Icons.Rounded.WavingHand,
                    trailingIcon = null,
                ) {
                    settingsViewModel.showAppIntro()
                }

                ListItem(
                    text = stringResource(Res.string.system_settings),
                    leadingIcon = Icons.Rounded.AppSettingsAlt,
                    trailingIcon = null,
                ) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.fromParts("package", context.packageName, null)
                    settingsLauncher.launch(intent)
                }

                ListItem(
                    text = stringResource(Res.string.acknowledgements),
                    leadingIcon = Icons.Rounded.Info,
                    trailingIcon = null,
                ) {
                    onNavigate(SettingsRoutes.About)
                }

                AppVersionButton(
                    excludedModulesUnlocked = excludedModulesUnlocked,
                    appVersionName = settingsViewModel.appVersionName,
                ) {
                    settingsViewModel.unlockExcludedModules()
                }
            }
        }
    }
}

private const val UNLOCK_CLICK_COUNT = 5 // Number of clicks required to unlock excluded modules.
private const val UNLOCKED_CLICK_COUNT = 3 // Number of clicks before we toast that modules are already unlocked.
private const val UNLOCK_TIMEOUT_SECONDS = 1 // Timeout in seconds to reset the click counter.

/** A button to display the app version. Clicking it 5 times will unlock the excluded modules. */
@Composable
private fun AppVersionButton(
    excludedModulesUnlocked: Boolean,
    appVersionName: String,
    onUnlockExcludedModules: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var clickCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(clickCount) {
        if (clickCount in 1..<UNLOCK_CLICK_COUNT) {
            delay(UNLOCK_TIMEOUT_SECONDS.seconds)
            clickCount = 0
        }
    }

    ListItem(
        text = stringResource(Res.string.app_version),
        leadingIcon = Icons.Rounded.Memory,
        supportingText = appVersionName,
        trailingIcon = null,
    ) {
        clickCount = clickCount.inc().coerceIn(0, UNLOCK_CLICK_COUNT)

        when {
            clickCount == UNLOCKED_CLICK_COUNT && excludedModulesUnlocked -> {
                clickCount = 0
                scope.launch { context.showToast(Res.string.modules_already_unlocked) }
            }

            clickCount == UNLOCK_CLICK_COUNT -> {
                clickCount = 0
                onUnlockExcludedModules()
                scope.launch { context.showToast(Res.string.modules_unlocked) }
            }
        }
    }
}

@Composable
private fun LanguagePickerDialog(onDismiss: () -> Unit) {
    SettingsDialog(title = stringResource(Res.string.preferences_language), onDismiss = onDismiss) {
        languageMap().forEach { (languageTag, languageName) ->
            ListItem(text = languageName, trailingIcon = null) {
                LanguageUtils.setAppLocale(languageTag)
                onDismiss()
            }
        }
    }
}

private enum class ThemeOption(val label: StringResource, val mode: Int) {
    DYNAMIC(label = Res.string.dynamic, mode = MODE_DYNAMIC),
    LIGHT(label = Res.string.theme_light, mode = AppCompatDelegate.MODE_NIGHT_NO),
    DARK(label = Res.string.theme_dark, mode = AppCompatDelegate.MODE_NIGHT_YES),
    SYSTEM(label = Res.string.theme_system, mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
}

@Composable
private fun ThemePickerDialog(onClickTheme: (Int) -> Unit, onDismiss: () -> Unit) {
    SettingsDialog(title = stringResource(Res.string.choose_theme), onDismiss = onDismiss) {
        ThemeOption.entries.forEach { option ->
            ListItem(text = stringResource(option.label), trailingIcon = null) {
                onClickTheme(option.mode)
                onDismiss()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.wrapContentWidth().wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column {
                Text(
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                )

                Column(modifier = Modifier.verticalScroll(rememberScrollState())) { content() }
            }
        }
    }
}
