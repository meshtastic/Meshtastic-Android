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
@file:Suppress("MatchingDeclarationName")

package org.meshtastic.feature.settings.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.choose_theme
import org.meshtastic.core.resources.dynamic
import org.meshtastic.core.resources.theme_dark
import org.meshtastic.core.resources.theme_light
import org.meshtastic.core.resources.theme_system
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.theme.MODE_DYNAMIC

/** Theme modes that match AppCompatDelegate constants for cross-platform use. */
enum class ThemeOption(val label: StringResource, val mode: Int) {
    DYNAMIC(label = Res.string.dynamic, mode = MODE_DYNAMIC),
    LIGHT(label = Res.string.theme_light, mode = 1), // AppCompatDelegate.MODE_NIGHT_NO
    DARK(label = Res.string.theme_dark, mode = 2), // AppCompatDelegate.MODE_NIGHT_YES
    SYSTEM(label = Res.string.theme_system, mode = -1), // AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
}

/** Shared dialog for picking a theme option. Used by both Android and Desktop settings screens. */
@Composable
fun ThemePickerDialog(onClickTheme: (Int) -> Unit, onDismiss: () -> Unit) {
    MeshtasticDialog(
        title = stringResource(Res.string.choose_theme),
        onDismiss = onDismiss,
        text = {
            Column {
                ThemeOption.entries.forEach { option ->
                    ListItem(text = stringResource(option.label), trailingIcon = null) {
                        onClickTheme(option.mode)
                        onDismiss()
                    }
                }
            }
        },
    )
}
