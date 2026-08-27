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
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.UnitsOverride
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.choose_units
import org.meshtastic.core.resources.units_follow_system
import org.meshtastic.core.resources.units_imperial
import org.meshtastic.core.resources.units_metric
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MeshtasticDialog

/** The [UnitsOverride] choices with their labels; System default follows the OS locale. */
private enum class UnitsOption(val label: StringResource, val override: UnitsOverride) {
    SYSTEM(label = Res.string.units_follow_system, override = UnitsOverride.SYSTEM),
    METRIC(label = Res.string.units_metric, override = UnitsOverride.METRIC),
    IMPERIAL(label = Res.string.units_imperial, override = UnitsOverride.IMPERIAL),
}

/** Shared dialog for picking the display units. Used by both Android and Desktop settings screens. */
@Composable
fun UnitsPickerDialog(onClickUnits: (UnitsOverride) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    MeshtasticDialog(
        modifier = modifier,
        title = stringResource(Res.string.choose_units),
        onDismiss = onDismiss,
        text = {
            Column {
                UnitsOption.entries.forEach { option ->
                    ListItem(text = stringResource(option.label), trailingIcon = null) {
                        onClickUnits(option.override)
                        onDismiss()
                    }
                }
            }
        },
    )
}
