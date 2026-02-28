/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
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

    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        availableTimeFrames.forEachIndexed { index, timeFrame ->
            val text = stringResource(timeFrame.strRes)
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index, availableTimeFrames.size),
                onClick = { onTimeFrameSelected(timeFrame) },
                selected = timeFrame == selectedTimeFrame,
                label = { Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}
