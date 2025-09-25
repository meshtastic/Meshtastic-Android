/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings.ACTION_APP_LOCALE_SETTINGS
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Output
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.ClientOnlyProtos.DeviceProfile
import com.geeksville.mesh.android.gpsDisabled
import com.geeksville.mesh.navigation.getNavRouteFrom
import com.geeksville.mesh.ui.common.components.MainAppBar
import com.geeksville.mesh.ui.node.components.NodeMenuAction
import com.geeksville.mesh.ui.settings.components.SettingsItem
import com.geeksville.mesh.ui.settings.components.SettingsItemDetail
import com.geeksville.mesh.ui.settings.components.SettingsItemSwitch
import com.geeksville.mesh.ui.settings.radio.RadioConfigItemList
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel
import com.geeksville.mesh.ui.settings.radio.components.EditDeviceProfileDialog
import com.geeksville.mesh.ui.settings.radio.components.PacketResponseStateDialog
import com.geeksville.mesh.util.LanguageUtils
import com.geeksville.mesh.util.LanguageUtils.getLanguageMap
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.delay
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.MultipleChoiceAlertDialog
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.core.ui.theme.MODE_DYNAMIC
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
                stringResource(R.string.import_configuration)
            } else {
                stringResource(R.string.export_configuration)
            },
            deviceProfile = deviceProfile ?: viewModel.currentDeviceProfile,
            onConfirm = {
                showEditDeviceProfileDialog = false
                if (deviceProfile != null) {
                    viewModel.installProfile(it)
                } else {
                    deviceProfile = it
                    val nodeName = it.shortName.ifBlank { "node" }
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
                title = stringResource(R.string.bottom_nav_settings),
                subtitle =
                if (state.isLocal) {
                    ourNode?.user?.longName
                } else {
                    val remoteName = viewModel.destNode.value?.user?.longName ?: ""
                    stringResource(R.string.remotely_administrating, remoteName)
                },
                ourNode = ourNode,
                isConnected = isConnected,
                showNodeChip = ourNode != null && isConnected && state.isLocal,
                canNavigateUp = false,
                onNavigateUp = {},
                actions = {},
                onAction = { action ->
                    when (action) {
                        is NodeMenuAction.MoreDetails -> onClickNodeChip(action.node.num)
                        else -> {}
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(paddingValues).padding(16.dp)) {
            RadioConfigItemList(
                state = state,
                isManaged = localConfig.security.isManaged,
                excludedModulesUnlocked = excludedModulesUnlocked,
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

            TitledCard(title = stringResource(R.string.app_settings), modifier = Modifier.padding(top = 16.dp)) {
                if (state.analyticsAvailable) {
                    SettingsItemSwitch(
                        text = stringResource(R.string.analytics_okay),
                        checked = state.analyticsEnabled,
                        leadingIcon = Icons.Default.BugReport,
                        onClick = { viewModel.toggleAnalytics() },
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
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.location_disabled),
                                    Toast.LENGTH_LONG,
                                )
                                    .show()
                            }
                        } else {
                            // Request permissions if not granted and user wants to provide location
                            locationPermissionsState.launchMultiplePermissionRequest()
                        }
                    } else {
                        settingsViewModel.meshService?.stopProvideLocation()
                    }
                }

                SettingsItemSwitch(
                    text = stringResource(R.string.provide_location_to_mesh),
                    leadingIcon = Icons.Rounded.LocationOn,
                    enabled = !isGpsDisabled,
                    checked = provideLocation,
                ) {
                    settingsViewModel.setProvideLocation(!provideLocation)
                }

                val settingsLauncher =
                    rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {}
                // On Android 12 and below, system app settings for language are not available. Use the in-app language
                // picker for these devices.
                val useInAppLangPicker = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                SettingsItem(
                    text = stringResource(R.string.preferences_language),
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

                SettingsItem(
                    text = stringResource(R.string.theme),
                    leadingIcon = Icons.Rounded.FormatPaint,
                    trailingIcon = null,
                ) {
                    showThemePickerDialog = true
                }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

                val exportRangeTestLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        if (it.resultCode == RESULT_OK) {
                            it.data?.data?.let { uri -> settingsViewModel.saveDataCsv(uri) }
                        }
                    }
                SettingsItem(
                    text = stringResource(R.string.save_rangetest),
                    leadingIcon = Icons.Rounded.Output,
                    trailingIcon = null,
                ) {
                    val intent =
                        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/csv"
                            putExtra(Intent.EXTRA_TITLE, "Meshtastic_rangetest_$timestamp.csv")
                        }
                    exportRangeTestLauncher.launch(intent)
                }

                val exportDataLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                        if (it.resultCode == RESULT_OK) {
                            it.data?.data?.let { uri -> settingsViewModel.saveDataCsv(uri) }
                        }
                    }
                SettingsItem(
                    text = stringResource(R.string.export_data_csv),
                    leadingIcon = Icons.Rounded.Output,
                    trailingIcon = null,
                ) {
                    val intent =
                        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/csv"
                            putExtra(Intent.EXTRA_TITLE, "Meshtastic_datalog_$timestamp.csv")
                        }
                    exportDataLauncher.launch(intent)
                }

                SettingsItem(
                    text = stringResource(R.string.intro_show),
                    leadingIcon = Icons.Rounded.WavingHand,
                    trailingIcon = null,
                ) {
                    settingsViewModel.showAppIntro()
                }

                AppVersionButton(excludedModulesUnlocked) { settingsViewModel.unlockExcludedModules() }
            }
        }
    }
}

private const val UNLOCK_CLICK_COUNT = 5 // Number of clicks required to unlock excluded modules.
private const val UNLOCKED_CLICK_COUNT = 3 // Number of clicks before we toast that modules are already unlocked.
private const val UNLOCK_TIMEOUT_SECONDS = 1 // Timeout in seconds to reset the click counter.

/** A button to display the app version. Clicking it 5 times will unlock the excluded modules. */
@Composable
private fun AppVersionButton(excludedModulesUnlocked: Boolean, onUnlockExcludedModules: () -> Unit) {
    val context = LocalContext.current
    var clickCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(clickCount) {
        if (clickCount in 1..<UNLOCK_CLICK_COUNT) {
            delay(UNLOCK_TIMEOUT_SECONDS.seconds)
            clickCount = 0
        }
    }

    SettingsItemDetail(
        text = stringResource(R.string.app_version),
        icon = Icons.Rounded.Memory,
        trailingText = BuildConfig.VERSION_NAME,
    ) {
        clickCount = clickCount.inc().coerceIn(0, UNLOCK_CLICK_COUNT)

        when {
            clickCount == UNLOCKED_CLICK_COUNT && excludedModulesUnlocked -> {
                clickCount = 0
                Toast.makeText(context, context.getString(R.string.modules_already_unlocked), Toast.LENGTH_LONG).show()
            }
            clickCount == UNLOCK_CLICK_COUNT -> {
                clickCount = 0
                onUnlockExcludedModules()
                Toast.makeText(context, context.getString(R.string.modules_unlocked), Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
private fun LanguagePickerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val choices = remember {
        context
            .getLanguageMap()
            .map { (languageTag, languageName) -> languageName to { LanguageUtils.setAppLocale(languageTag) } }
            .toMap()
    }

    MultipleChoiceAlertDialog(
        title = stringResource(R.string.preferences_language),
        message = "",
        choices = choices,
        onDismissRequest = onDismiss,
    )
}

@Composable
private fun ThemePickerDialog(onClickTheme: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val themeMap = remember {
        mapOf(
            context.getString(R.string.dynamic) to MODE_DYNAMIC,
            context.getString(R.string.theme_light) to AppCompatDelegate.MODE_NIGHT_NO,
            context.getString(R.string.theme_dark) to AppCompatDelegate.MODE_NIGHT_YES,
            context.getString(R.string.theme_system) to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        )
            .mapValues { (_, value) -> { onClickTheme(value) } }
    }

    MultipleChoiceAlertDialog(
        title = stringResource(R.string.choose_theme),
        message = "",
        choices = themeMap,
        onDismissRequest = onDismiss,
    )
}
