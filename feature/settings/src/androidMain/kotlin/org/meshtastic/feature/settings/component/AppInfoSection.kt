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
package org.meshtastic.feature.settings.component

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.acknowledgements
import org.meshtastic.core.resources.app_notifications
import org.meshtastic.core.resources.app_version
import org.meshtastic.core.resources.info
import org.meshtastic.core.resources.intro_show
import org.meshtastic.core.resources.modules_already_unlocked
import org.meshtastic.core.resources.modules_unlocked
import org.meshtastic.core.resources.system_settings
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.icon.AppSettingsAlt
import org.meshtastic.core.ui.icon.ChevronRight
import org.meshtastic.core.ui.icon.Info
import org.meshtastic.core.ui.icon.Memory
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Notifications
import org.meshtastic.core.ui.icon.WavingHand
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.showToast
import kotlin.time.Duration.Companion.seconds

/** Section displaying application information and related actions. */
@Composable
fun AppInfoSection(
    appVersionName: String,
    excludedModulesUnlocked: Boolean,
    onUnlockExcludedModules: () -> Unit,
    onShowAppIntro: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    val context = LocalContext.current
    val settingsLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {}

    ExpressiveSection(title = stringResource(Res.string.info)) {
        ListItem(
            text = stringResource(Res.string.intro_show),
            leadingIcon = MeshtasticIcons.WavingHand,
            trailingIcon = null,
        ) {
            onShowAppIntro()
        }

        ListItem(
            text = stringResource(Res.string.app_notifications),
            leadingIcon = MeshtasticIcons.Notifications,
            trailingIcon = null,
        ) {
            val intent =
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            settingsLauncher.launch(intent)
        }

        ListItem(
            text = stringResource(Res.string.system_settings),
            leadingIcon = MeshtasticIcons.AppSettingsAlt,
            trailingIcon = null,
        ) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", context.packageName, null)
            settingsLauncher.launch(intent)
        }

        ListItem(
            text = stringResource(Res.string.acknowledgements),
            leadingIcon = MeshtasticIcons.Info,
            trailingIcon = MeshtasticIcons.ChevronRight,
        ) {
            onNavigateToAbout()
        }

        AppVersionButton(
            excludedModulesUnlocked = excludedModulesUnlocked,
            appVersionName = appVersionName,
            onUnlockExcludedModules = onUnlockExcludedModules,
        )
    }
}

private const val UNLOCK_CLICK_COUNT = 5 // Number of clicks required to unlock excluded modules.
private const val UNLOCKED_CLICK_COUNT = 3 // Number of clicks before we toast that modules are already unlocked.
private const val UNLOCK_TIMEOUT_SECONDS = 1 // Timeout in seconds to reset the click counter.

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
        leadingIcon = MeshtasticIcons.Memory,
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

@Preview(showBackground = true)
@Composable
private fun AppInfoSectionPreview() {
    AppTheme {
        AppInfoSection(
            appVersionName = "2.5.0",
            excludedModulesUnlocked = false,
            onUnlockExcludedModules = {},
            onShowAppIntro = {},
            onNavigateToAbout = {},
        )
    }
}
