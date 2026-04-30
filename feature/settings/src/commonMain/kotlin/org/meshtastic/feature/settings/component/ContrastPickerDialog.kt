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
import org.meshtastic.core.resources.choose_contrast
import org.meshtastic.core.resources.contrast_high
import org.meshtastic.core.resources.contrast_medium
import org.meshtastic.core.resources.contrast_standard
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.theme.ContrastLevel

/** Contrast level options matching [ContrastLevel] ordinal values. */
enum class ContrastOption(val label: StringResource, val level: ContrastLevel) {
    STANDARD(label = Res.string.contrast_standard, level = ContrastLevel.STANDARD),
    MEDIUM(label = Res.string.contrast_medium, level = ContrastLevel.MEDIUM),
    HIGH(label = Res.string.contrast_high, level = ContrastLevel.HIGH),
}

/** Shared dialog for picking a contrast level. Used by both Android and Desktop settings screens. */
@Composable
fun ContrastPickerDialog(onClickContrast: (Int) -> Unit, onDismiss: () -> Unit) {
    MeshtasticDialog(
        title = stringResource(Res.string.choose_contrast),
        onDismiss = onDismiss,
        text = {
            Column {
                ContrastOption.entries.forEach { option ->
                    ListItem(text = stringResource(option.label), trailingIcon = null) {
                        onClickContrast(option.level.value)
                        onDismiss()
                    }
                }
            }
        },
    )
}
