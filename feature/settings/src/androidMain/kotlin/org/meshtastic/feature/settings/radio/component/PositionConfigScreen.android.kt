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

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.location.LocationCompat
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Position
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.position_config_set_fixed_from_phone
import org.meshtastic.feature.settings.radio.RadioConfigViewModel

@Composable
actual fun DeviceLocationButton(
    viewModel: RadioConfigViewModel,
    enabled: Boolean,
    onLocationReceived: (Position) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    TextButton(
        enabled = enabled,
        onClick = {
            @SuppressLint("MissingPermission")
            coroutineScope.launch {
                val phoneLoc = viewModel.getCurrentLocation()
                if (phoneLoc != null) {
                    val locationInput =
                        Position(
                            latitude = phoneLoc.latitude,
                            longitude = phoneLoc.longitude,
                            altitude =
                            LocationCompat.hasMslAltitude(phoneLoc).let {
                                if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                    phoneLoc.mslAltitudeMeters.toInt()
                                } else {
                                    phoneLoc.altitude.toInt()
                                }
                            },
                        )
                    onLocationReceived(locationInput)
                }
            }
        },
    ) {
        Text(text = stringResource(Res.string.position_config_set_fixed_from_phone))
    }
}
