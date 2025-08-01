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

package com.geeksville.mesh.ui.metrics

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig.DisplayUnits
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.util.metersIn
import com.geeksville.mesh.util.toString
import java.text.DateFormat
import kotlin.time.Duration.Companion.days

@Composable
private fun RowScope.PositionText(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
    )
}

private const val WEIGHT_10 = .10f
private const val WEIGHT_15 = .15f
private const val WEIGHT_20 = .20f
private const val WEIGHT_40 = .40f

@Composable
private fun HeaderItem(compactWidth: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        PositionText(stringResource(R.string.latitude), WEIGHT_20)
        PositionText(stringResource(R.string.longitude), WEIGHT_20)
        PositionText(stringResource(R.string.sats), WEIGHT_10)
        PositionText(stringResource(R.string.alt), WEIGHT_15)
        if (!compactWidth) {
            PositionText(stringResource(R.string.speed), WEIGHT_15)
            PositionText(stringResource(R.string.heading), WEIGHT_15)
        }
        PositionText(stringResource(R.string.timestamp), WEIGHT_40)
    }
}

private const val DEG_D = 1e-7
private const val HEADING_DEG = 1e-5
private const val SECONDS_TO_MILLIS = 1000L

@Composable
private fun PositionItem(
    compactWidth: Boolean,
    position: MeshProtos.Position,
    dateFormat: DateFormat,
    system: DisplayUnits,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PositionText("%.5f".format(position.latitudeI * DEG_D), WEIGHT_20)
        PositionText("%.5f".format(position.longitudeI * DEG_D), WEIGHT_20)
        PositionText(position.satsInView.toString(), WEIGHT_10)
        PositionText(position.altitude.metersIn(system).toString(system), WEIGHT_15)
        if (!compactWidth) {
            PositionText("${position.groundSpeed} Km/h", WEIGHT_15)
            PositionText("%.0f°".format(position.groundTrack * HEADING_DEG), WEIGHT_15)
        }
        PositionText(formatPositionTime(position, dateFormat), WEIGHT_40)
    }
}

@Composable
private fun formatPositionTime(position: MeshProtos.Position, dateFormat: DateFormat): String {
    val currentTime = System.currentTimeMillis()
    val sixMonthsAgo = currentTime - 180.days.inWholeMilliseconds
    val isOlderThanSixMonths = position.time * SECONDS_TO_MILLIS < sixMonthsAgo
    val timeText =
        if (isOlderThanSixMonths) {
            stringResource(id = R.string.unknown_age)
        } else {
            dateFormat.format(position.time * SECONDS_TO_MILLIS)
        }
    return timeText
}

@Composable
private fun ActionButtons(
    clearButtonEnabled: Boolean,
    onClear: () -> Unit,
    saveButtonEnabled: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = onClear,
            enabled = clearButtonEnabled,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = stringResource(id = R.string.clear))
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.clear))
        }

        OutlinedButton(modifier = Modifier.weight(1f), onClick = onSave, enabled = saveButtonEnabled) {
            Icon(imageVector = Icons.Default.Save, contentDescription = stringResource(id = R.string.save))
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.save))
        }
    }
}

@Composable
fun PositionLogScreen(viewModel: MetricsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val exportPositionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                it.data?.data?.let { uri -> viewModel.savePositionCSV(uri) }
            }
        }

    var clearButtonEnabled by rememberSaveable(state.positionLogs) { mutableStateOf(state.positionLogs.isNotEmpty()) }

    BoxWithConstraints {
        val compactWidth = maxWidth < 600.dp
        Column {
            val textStyle =
                if (compactWidth) {
                    MaterialTheme.typography.bodySmall
                } else {
                    LocalTextStyle.current
                }
            CompositionLocalProvider(LocalTextStyle provides textStyle) {
                HeaderItem(compactWidth)
                PositionList(compactWidth, state.positionLogs, state.displayUnits)
            }

            ActionButtons(
                clearButtonEnabled = clearButtonEnabled,
                onClear = {
                    clearButtonEnabled = false
                    viewModel.clearPosition()
                },
                saveButtonEnabled = state.hasPositionLogs(),
                onSave = {
                    val intent =
                        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/*"
                            putExtra(Intent.EXTRA_TITLE, "position.csv")
                        }
                    exportPositionLauncher.launch(intent)
                },
            )
        }
    }
}

@Composable
private fun ColumnScope.PositionList(
    compactWidth: Boolean,
    positions: List<MeshProtos.Position>,
    displayUnits: DisplayUnits,
) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM) }

    LazyColumn(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        items(positions) { position -> PositionItem(compactWidth, position, dateFormat, displayUnits) }
    }
}

@Suppress("MagicNumber")
private val testPosition =
    MeshProtos.Position.newBuilder()
        .apply {
            latitudeI = 297604270
            longitudeI = -953698040
            altitude = 1230
            satsInView = 7
            time = (System.currentTimeMillis() / 1000).toInt()
        }
        .build()

@Preview(showBackground = true)
@Composable
private fun PositionItemPreview() {
    AppTheme {
        PositionItem(
            compactWidth = false,
            position = testPosition,
            dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM),
            system = DisplayUnits.METRIC,
        )
    }
}

@PreviewScreenSizes
@Composable
private fun ActionButtonsPreview() {
    AppTheme {
        Column(Modifier.fillMaxSize(), Arrangement.Bottom) {
            ActionButtons(clearButtonEnabled = true, onClear = {}, saveButtonEnabled = true, onSave = {})
        }
    }
}
