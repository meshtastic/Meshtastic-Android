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
package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.common.util.LocaleUnitsProvider
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.model.util.MAX_PUBLIC_KEY_PRECISION_BITS
import org.meshtastic.core.model.util.onWirePrecisionBits
import org.meshtastic.core.model.util.precisionRadiusMetersOrNull
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.position_enabled
import org.meshtastic.core.resources.precise_location
import org.meshtastic.core.resources.precise_location_insecure_channel
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.math.roundToInt

private const val POSITION_ENABLED = 32
private const val POSITION_DISABLED = 0

private const val POSITION_PRECISION_MIN = 10
private const val POSITION_PRECISION_MAX = 19
private const val POSITION_PRECISION_DEFAULT = 13

@Composable
fun PositionPrecisionPreference(
    value: Int,
    enabled: Boolean,
    onValueChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    usesPublicKey: Boolean = false,
) {
    // Firmware 2.8 caps a public key at this on the wire; the controls apply the same cap on every firmware.
    val shown = onWirePrecisionBits(value, usesPublicKey)
    val finest = if (usesPublicKey) MAX_PUBLIC_KEY_PRECISION_BITS else POSITION_PRECISION_MAX
    val unit =
        if (LocalInspectionMode.current) {
            // Previews and screenshot tests render with no Koin application; the value itself is arbitrary there.
            MeasurementSystem.METRIC
        } else {
            koinInject<LocaleUnitsProvider>().measurementSystem.collectAsStateWithLifecycle().value
        }

    Column(modifier = modifier) {
        SwitchPreference(
            title = stringResource(Res.string.position_enabled),
            checked = value != POSITION_DISABLED,
            enabled = enabled,
            onCheckedChange = { enabled ->
                val newValue =
                    when {
                        !enabled -> POSITION_DISABLED
                        usesPublicKey -> POSITION_PRECISION_DEFAULT
                        else -> POSITION_ENABLED
                    }
                onValueChanged(newValue)
            },
            padding = PaddingValues(0.dp),
        )
        if (value != POSITION_DISABLED) {
            SwitchPreference(
                title = stringResource(Res.string.precise_location),
                summary = if (usesPublicKey) stringResource(Res.string.precise_location_insecure_channel) else "",
                checked = shown == POSITION_ENABLED,
                enabled = enabled && !usesPublicKey,
                onCheckedChange = { enabled ->
                    val newValue = if (enabled) POSITION_ENABLED else POSITION_PRECISION_DEFAULT
                    onValueChanged(newValue)
                },
                padding = PaddingValues(0.dp),
            )
        }
        if (shown in (POSITION_DISABLED + 1) until POSITION_ENABLED) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Slider(
                    value = shown.toFloat(),
                    onValueChange = { onValueChanged(it.roundToInt()) },
                    enabled = enabled,
                    valueRange = POSITION_PRECISION_MIN.toFloat()..finest.toFloat(),
                    steps = finest - POSITION_PRECISION_MIN - 1,
                )

                val precisionMeters = precisionRadiusMetersOrNull(shown)?.toInt()
                Text(
                    text = "± ${precisionMeters?.toDistanceString(unit).orEmpty()}",
                    modifier = Modifier.padding(bottom = 16.dp),
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PositionPrecisionPreferencePreview() {
    AppTheme {
        PositionPrecisionPreference(
            value = POSITION_PRECISION_DEFAULT,
            enabled = true,
            onValueChanged = {},
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
