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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.UnitsOverride
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.choose_units
import org.meshtastic.core.resources.units_follow_system
import org.meshtastic.core.resources.units_imperial
import org.meshtastic.core.resources.units_metric
import org.meshtastic.core.resources.units_scope_summary
import org.meshtastic.core.ui.component.MeshtasticDialog

/** The [UnitsOverride] choices with their labels; System default follows the OS locale. */
enum class UnitsOption(val label: StringResource, val override: UnitsOverride) {
    SYSTEM(label = Res.string.units_follow_system, override = UnitsOverride.SYSTEM),
    METRIC(label = Res.string.units_metric, override = UnitsOverride.METRIC),
    IMPERIAL(label = Res.string.units_imperial, override = UnitsOverride.IMPERIAL),
}

/**
 * Shared single-choice dialog for the display units. Used by both Android and Desktop settings screens.
 *
 * The copy names the setting's scope explicitly — this app's display, not the radio's own screen — because "set the
 * device Display to metric, app still shows miles" is the perennial confusion in every units thread (#6840): the
 * radio's DisplayConfig only ever drove the device screen.
 */
@Composable
fun UnitsPickerDialog(
    current: UnitsOverride,
    onClickUnits: (UnitsOverride) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MeshtasticDialog(
        modifier = modifier,
        title = stringResource(Res.string.choose_units),
        onDismiss = onDismiss,
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.units_scope_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Column(modifier = Modifier.selectableGroup()) {
                    UnitsOption.entries.forEach { option ->
                        val selected = option.override == current
                        Row(
                            modifier =
                            Modifier.fillMaxWidth()
                                .height(48.dp)
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onClickUnits(option.override)
                                        onDismiss()
                                    },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Text(
                                text = stringResource(option.label),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        },
    )
}
