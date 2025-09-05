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

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Output
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ClientOnlyProtos.DeviceProfile
import com.geeksville.mesh.DeviceUIProtos.Language
import com.geeksville.mesh.R
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.navigation.Route
import com.geeksville.mesh.navigation.getNavRouteFrom
import com.geeksville.mesh.ui.common.components.TitledCard
import com.geeksville.mesh.ui.common.theme.MODE_DYNAMIC
import com.geeksville.mesh.ui.settings.components.SettingsItem
import com.geeksville.mesh.ui.settings.components.SettingsItemSwitch
import com.geeksville.mesh.ui.settings.radio.RadioConfigItemList
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel
import com.geeksville.mesh.ui.settings.radio.components.EditDeviceProfileDialog
import com.geeksville.mesh.ui.settings.radio.components.PacketResponseStateDialog
import com.geeksville.mesh.util.LanguageUtils

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun SettingsScreen(
    viewModel: RadioConfigViewModel = hiltViewModel(),
    uiViewModel: UIViewModel = hiltViewModel(),
    onNavigate: (Route) -> Unit = {},
) {
    uiViewModel.setTitle(stringResource(R.string.bottom_nav_settings))

    val excludedModulesUnlocked by uiViewModel.excludedModulesUnlocked.collectAsStateWithLifecycle()
    val localConfig by uiViewModel.localConfig.collectAsStateWithLifecycle()

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
                    val intent =
                        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/*"
                            putExtra(Intent.EXTRA_TITLE, "device_profile.cfg")
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

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
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

        TitledCard(title = stringResource(R.string.phone_settings), modifier = Modifier.padding(top = 16.dp)) {
            if (state.analyticsAvailable) {
                SettingsItemSwitch(
                    text = stringResource(R.string.analytics_okay),
                    checked = state.analyticsEnabled,
                    leadingIcon = Icons.Default.BugReport,
                    onClick = { viewModel.toggleAnalytics() },
                )
            }

            val context = LocalContext.current
            val languageTags = remember { LanguageUtils.getLanguageTags(context) }
            SettingsItem(
                text = stringResource(R.string.preferences_language),
                leadingIcon = Icons.Rounded.Language,
                trailingIcon = null,
            ) {
                val lang = LanguageUtils.getLocale()
                debug("Lang from prefs: $lang")
                val langMap = languageTags.mapValues { (_, value) -> { LanguageUtils.setLocale(value) } }

                uiViewModel.showAlert(
                    title = context.getString(R.string.preferences_language),
                    message = "",
                    choices = langMap,
                )
            }

            val themeMap = remember {
                mapOf(
                    context.getString(R.string.dynamic) to MODE_DYNAMIC,
                    context.getString(R.string.theme_light) to AppCompatDelegate.MODE_NIGHT_NO,
                    context.getString(R.string.theme_dark) to AppCompatDelegate.MODE_NIGHT_YES,
                    context.getString(R.string.theme_system) to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                )
            }
            SettingsItem(
                text = stringResource(R.string.theme),
                leadingIcon = Icons.Rounded.FormatPaint,
                trailingIcon = null,
            ) {
                uiViewModel.showAlert(
                    title = context.getString(R.string.choose_theme),
                    message = "",
                    choices = themeMap.mapValues { (_, value) -> { uiViewModel.setTheme(value) } },
                )
            }

            val exportRangeTestLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    if (it.resultCode == RESULT_OK) {
                        it.data?.data?.let { uri -> uiViewModel.saveRangeTestCsv(uri) }
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
                        putExtra(Intent.EXTRA_TITLE, "rangetest.csv")
                    }
                exportRangeTestLauncher.launch(intent)
            }

            SettingsItem(
                text = stringResource(R.string.intro_show),
                leadingIcon = Icons.Rounded.WavingHand,
                trailingIcon = null,
            ) {
                uiViewModel.showAppIntro()
            }
        }
    }
}
