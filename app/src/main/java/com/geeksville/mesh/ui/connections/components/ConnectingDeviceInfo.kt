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
package com.geeksville.mesh.ui.connections.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.connecting
import org.meshtastic.core.strings.disconnect
import org.meshtastic.core.ui.theme.StatusColors.StatusRed

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectingDeviceInfo(deviceName: String, deviceAddress: String, onClickDisconnect: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularWavyProgressIndicator(modifier = Modifier.size(40.dp))

            Column {
                Text(text = deviceName, style = MaterialTheme.typography.titleMedium)
                Text(text = deviceAddress, style = MaterialTheme.typography.bodySmall)
                Text(text = stringResource(Res.string.connecting), style = MaterialTheme.typography.labelSmall)
            }
        }

        Button(
            shape = RectangleShape,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.StatusRed,
                contentColor = Color.White,
            ),
            onClick = onClickDisconnect,
        ) {
            Text(stringResource(Res.string.disconnect))
        }
    }
}
