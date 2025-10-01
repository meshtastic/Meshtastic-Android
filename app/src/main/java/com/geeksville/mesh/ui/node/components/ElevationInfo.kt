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

package com.geeksville.mesh.ui.node.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig.DisplayUnits
import org.meshtastic.core.model.util.metersIn
import org.meshtastic.core.model.util.toString
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.icon.Elevation
import org.meshtastic.core.ui.icon.MeshtasticIcons

@Composable
fun ElevationInfo(modifier: Modifier = Modifier, altitude: Int, system: DisplayUnits, suffix: String) {
    IconInfo(
        modifier = modifier,
        icon = MeshtasticIcons.Elevation,
        contentDescription = stringResource(R.string.altitude),
        text = altitude.metersIn(system).toString(system),
    )
}

@Composable
@Preview
fun ElevationInfoPreview() {
    MaterialTheme { ElevationInfo(altitude = 100, system = DisplayUnits.METRIC, suffix = "ASL") }
}
