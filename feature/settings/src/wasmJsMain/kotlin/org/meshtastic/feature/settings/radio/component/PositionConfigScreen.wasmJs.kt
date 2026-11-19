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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Position
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.position_config_set_fixed_from_phone
import org.meshtastic.core.resources.position_location_blocked
import org.meshtastic.core.resources.position_location_unavailable
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.rememberLocationPermissionState
import org.meshtastic.feature.settings.radio.RadioConfigViewModel

/**
 * Fills the fixed position from the browser's Geolocation API; the browser asks for permission on this click. A click
 * that brings back no fix says why underneath, since a blocked permission makes the browser refuse without a prompt.
 */
@Composable
@Suppress("ModifierMissing") // the expect signature has no modifier to take
actual fun DeviceLocationButton(
    viewModel: RadioConfigViewModel,
    enabled: Boolean,
    onLocationReceived: (Position) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val permission = rememberLocationPermissionState()
    var noFix by remember { mutableStateOf(false) }

    Column {
        TextButton(
            enabled = enabled,
            onClick = {
                coroutineScope.launch {
                    val phoneLoc = viewModel.getCurrentLocation()
                    noFix = phoneLoc == null
                    if (phoneLoc != null) {
                        onLocationReceived(
                            Position(
                                latitude = phoneLoc.latitude,
                                longitude = phoneLoc.longitude,
                                altitude = (phoneLoc.mslAltitudeMeters ?: phoneLoc.altitudeMeters ?: 0.0).toInt(),
                            ),
                        )
                    }
                }
            },
        ) {
            Text(text = stringResource(Res.string.position_config_set_fixed_from_phone))
        }
        val blocked = permission.status == PermissionStatus.PERMANENTLY_DENIED
        if (blocked || noFix) {
            Text(
                text =
                stringResource(
                    if (blocked) Res.string.position_location_blocked else Res.string.position_location_unavailable,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}
