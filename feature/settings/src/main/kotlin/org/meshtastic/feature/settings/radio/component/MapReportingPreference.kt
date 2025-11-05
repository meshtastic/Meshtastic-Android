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

package org.meshtastic.feature.settings.radio.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.util.DistanceUnit
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.precisionBitsToMeters
import org.meshtastic.feature.settings.util.IntervalConfiguration
import org.meshtastic.feature.settings.util.toDisplayString
import kotlin.math.roundToInt
import org.meshtastic.core.strings.R as Res

private const val POSITION_PRECISION_MIN = 12
private const val POSITION_PRECISION_MAX = 15

@Suppress("LongMethod")
@Composable
fun MapReportingPreference(
    mapReportingEnabled: Boolean = false,
    onMapReportingEnabledChanged: (Boolean) -> Unit = {},
    shouldReportLocation: Boolean = false,
    onShouldReportLocationChanged: (Boolean) -> Unit = {},
    positionPrecision: Int = 14,
    onPositionPrecisionChanged: (Int) -> Unit = {},
    publishIntervalSecs: Int = 3600,
    onPublishIntervalSecsChanged: (Int) -> Unit = {},
    enabled: Boolean,
) {
    Column {
        var showMapReportingWarning by rememberSaveable { mutableStateOf(mapReportingEnabled) }
        LaunchedEffect(mapReportingEnabled) { showMapReportingWarning = mapReportingEnabled }
        SwitchPreference(
            title = stringResource(Res.string.map_reporting),
            summary = stringResource(Res.string.map_reporting_summary),
            checked = showMapReportingWarning,
            enabled = enabled,
            onCheckedChange = { checked ->
                showMapReportingWarning = checked
                if (checked && shouldReportLocation) {
                    onMapReportingEnabledChanged(true)
                } else if (!checked) {
                    onMapReportingEnabledChanged(false)
                }
            },
        )
        AnimatedVisibility(showMapReportingWarning) {
            Card(modifier = Modifier.padding(16.dp)) {
                Text(text = stringResource(Res.string.map_reporting_consent_header), modifier = Modifier.padding(16.dp))
                HorizontalDivider()
                Text(stringResource(Res.string.map_reporting_consent_text), modifier = Modifier.padding(16.dp))

                SwitchPreference(
                    title = stringResource(Res.string.i_agree),
                    summary = stringResource(Res.string.i_agree_to_share_my_location),
                    checked = shouldReportLocation,
                    enabled = enabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            onMapReportingEnabledChanged(true)
                            onShouldReportLocationChanged(true)
                        } else {
                            onShouldReportLocationChanged(false)
                        }
                    },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                if (shouldReportLocation && mapReportingEnabled) {
                    Slider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        value = positionPrecision.toFloat(),
                        onValueChange = { onPositionPrecisionChanged(it.roundToInt()) },
                        enabled = enabled,
                        valueRange = POSITION_PRECISION_MIN.toFloat()..POSITION_PRECISION_MAX.toFloat(),
                        steps = POSITION_PRECISION_MAX - POSITION_PRECISION_MIN - 1,
                    )
                    val precisionMeters = precisionBitsToMeters(positionPrecision).toInt()
                    val unit = DistanceUnit.Companion.getFromLocale()
                    Text(
                        text = precisionMeters.toDistanceString(unit),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        overflow = TextOverflow.Companion.Ellipsis,
                        maxLines = 1,
                    )
                    val publishItems = remember { IntervalConfiguration.BROADCAST_MEDIUM.allowedIntervals }
                    DropDownPreference(
                        modifier = Modifier.padding(bottom = 16.dp),
                        title = stringResource(Res.string.map_reporting_interval_seconds),
                        items = publishItems.map { it.value to it.toDisplayString() },
                        selectedItem = publishIntervalSecs,
                        enabled = enabled,
                        onItemSelected = { onPublishIntervalSecsChanged(it.toInt()) },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MapReportingPreview() {
    MapReportingPreference(
        mapReportingEnabled = true,
        onMapReportingEnabledChanged = {},
        shouldReportLocation = true,
        onShouldReportLocationChanged = {},
        positionPrecision = 5,
        onPositionPrecisionChanged = {},
        enabled = true,
    )
}
