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
package org.meshtastic.feature.settings.component

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.Language
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.app_settings
import org.meshtastic.core.resources.preferences_language
import org.meshtastic.core.resources.theme
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.feature.settings.radio.ExpressiveSection

@Composable
fun AppearanceSection(
    onShowLanguagePicker: () -> Unit,
    onShowThemePicker: () -> Unit,
) {
    val context = LocalContext.current
    val settingsLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {}

    // On Android 12 and below, system app settings for language are not available. Use the in-app language
    // picker for these devices.
    val useInAppLangPicker = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    ExpressiveSection(title = stringResource(Res.string.app_settings)) {
        ListItem(
            text = stringResource(Res.string.preferences_language),
            leadingIcon = Icons.Rounded.Language,
            trailingIcon = if (useInAppLangPicker) null else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        ) {
            if (useInAppLangPicker) {
                onShowLanguagePicker()
            } else {
                val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS, "package:${context.packageName}".toUri())
                if (intent.resolveActivity(context.packageManager) != null) {
                    settingsLauncher.launch(intent)
                } else {
                    // Fall back to the in-app picker
                    onShowLanguagePicker()
                }
            }
        }

        ListItem(
            text = stringResource(Res.string.theme),
            leadingIcon = Icons.Rounded.FormatPaint,
            trailingIcon = null,
        ) {
            onShowThemePicker()
        }
    }
}
