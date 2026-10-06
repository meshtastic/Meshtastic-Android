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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.about
import org.meshtastic.core.resources.app_notifications
import org.meshtastic.core.resources.info
import org.meshtastic.core.resources.intro_show
import org.meshtastic.core.resources.system_settings
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.icon.AppSettingsAlt
import org.meshtastic.core.ui.icon.ChevronRight
import org.meshtastic.core.ui.icon.Info
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Notifications
import org.meshtastic.core.ui.icon.WavingHand
import org.meshtastic.core.ui.theme.AppTheme

/** Section displaying application information and related actions. */
@Composable
fun AppInfoSection(
    appVersionName: String,
    hiddenFeaturesUnlocked: Boolean,
    onUnlockHiddenFeatures: () -> Unit,
    onShowAppIntro: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onPlayChirpyHop: () -> Unit,
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
            text = stringResource(Res.string.about),
            leadingIcon = MeshtasticIcons.Info,
            trailingIcon = MeshtasticIcons.ChevronRight,
        ) {
            onNavigateToAbout()
        }

        AppVersionRows(
            hiddenFeaturesUnlocked = hiddenFeaturesUnlocked,
            appVersionName = appVersionName,
            onUnlockHiddenFeatures = onUnlockHiddenFeatures,
            onPlayChirpyHop = onPlayChirpyHop,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AppInfoSectionPreview() {
    AppTheme {
        AppInfoSection(
            appVersionName = "2.5.0",
            hiddenFeaturesUnlocked = false,
            onUnlockHiddenFeatures = {},
            onShowAppIntro = {},
            onNavigateToAbout = {},
            onPlayChirpyHop = {},
        )
    }
}
