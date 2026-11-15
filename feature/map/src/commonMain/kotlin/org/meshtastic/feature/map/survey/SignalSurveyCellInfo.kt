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
package org.meshtastic.feature.map.survey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.dbm_value
import org.meshtastic.core.resources.mesh_hud_snr
import org.meshtastic.core.resources.rssi
import org.meshtastic.core.resources.signal_survey_last_reading
import org.meshtastic.core.resources.signal_survey_readings
import org.meshtastic.core.resources.signal_survey_senders
import org.meshtastic.core.resources.snr

internal const val SIGNAL_SURVEY_CELL_INFO_TAG = "signal_survey_cell_info"

/** Shows the details of the square the user tapped. A map mounts this once, beside its other dialogs. */
@Composable
fun SignalSurveyCellInfo(viewModel: SignalSurveyViewModel = koinViewModel()) {
    val selected by viewModel.selectedCell.collectAsStateWithLifecycle()
    selected?.let { SignalSurveyCellDialog(it, onDismiss = { viewModel.selectCell(null) }) }
}

@Composable
internal fun SignalSurveyCellDialog(cell: SurveyCellUi, onDismiss: () -> Unit) {
    val reading = cell.cell
    AlertDialog(
        modifier = Modifier.testTag(SIGNAL_SURVEY_CELL_INFO_TAG),
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(16.dp).background(cell.quality.color(), RoundedCornerShape(3.dp)))
                Text(stringResource(cell.quality.nameRes))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                reading.bestSnr?.let { snr ->
                    val value = stringResource(Res.string.mesh_hud_snr, NumberFormatter.format(snr, 1))
                    Text("${stringResource(Res.string.snr)} $value")
                }
                Text("${stringResource(Res.string.rssi)} ${stringResource(Res.string.dbm_value, reading.rssi)}")
                Text(pluralStringResource(Res.plurals.signal_survey_readings, reading.samples, reading.samples))
                Text(pluralStringResource(Res.plurals.signal_survey_senders, reading.senders, reading.senders))
                Text(
                    stringResource(
                        Res.string.signal_survey_last_reading,
                        DateFormatter.formatDateTimeShort(reading.lastHeardMillis),
                    ),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } },
    )
}
