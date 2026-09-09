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
package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.overflow_menu
import org.meshtastic.core.ui.icon.Check
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.More
import org.meshtastic.feature.node.model.TimeFrame

@Suppress("LambdaParameterEventTrailing")
@Composable
fun TimeFrameSelector(
    selectedTimeFrame: TimeFrame,
    availableTimeFrames: List<TimeFrame>,
    onTimeFrameSelected: (TimeFrame) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (availableTimeFrames.size <= 1) return

    // Items that don't fit collapse into overflowIndicator's menu instead of squeezing every label.
    ButtonGroup(
        overflowIndicator = { menuState ->
            IconButton(onClick = { if (menuState.isShowing) menuState.dismiss() else menuState.show() }) {
                Icon(imageVector = MeshtasticIcons.More, contentDescription = stringResource(Res.string.overflow_menu))
            }
        },
        modifier = modifier.fillMaxWidth().selectableGroup(),
    ) {
        availableTimeFrames.forEach { timeFrame ->
            val isSelected = timeFrame == selectedTimeFrame
            customItem(
                buttonGroupContent = {
                    // ToggleButton hardcodes Role.Checkbox; override it so the group still
                    // reads as single-choice radio semantics to a screen reader.
                    OutlinedToggleButton(
                        checked = isSelected,
                        onCheckedChange = { onTimeFrameSelected(timeFrame) },
                        modifier =
                        Modifier.semantics {
                            role = Role.RadioButton
                            selected = isSelected
                        },
                    ) {
                        Text(text = stringResource(timeFrame.strRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(
                        text = { Text(text = stringResource(timeFrame.strRes)) },
                        onClick = {
                            onTimeFrameSelected(timeFrame)
                            menuState.dismiss()
                        },
                        trailingIcon =
                        if (isSelected) {
                            { Icon(imageVector = MeshtasticIcons.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        modifier =
                        Modifier.semantics {
                            role = Role.RadioButton
                            selected = isSelected
                        },
                    )
                },
            )
        }
    }
}
