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
package org.meshtastic.feature.discovery.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Suppress("MagicNumber")
private val CONTENT_PADDING = 8.dp
private const val SECONDS_PER_MINUTE = 60L

/** Displays dwell progress for a single preset with a countdown timer and linear progress bar. */
@Composable
fun DwellProgressIndicator(
    presetName: String,
    remainingSeconds: Long,
    totalSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val progress =
        if (totalSeconds > 0) {
            1f - (remainingSeconds.toFloat() / totalSeconds.toFloat())
        } else {
            0f
        }
    val minutes = remainingSeconds / SECONDS_PER_MINUTE
    val seconds = remainingSeconds % SECONDS_PER_MINUTE
    val timeText = "%02d:%02d".format(minutes, seconds)

    Column(verticalArrangement = Arrangement.spacedBy(CONTENT_PADDING), modifier = modifier.fillMaxWidth()) {
        Text(text = "Dwelling on $presetName", style = MaterialTheme.typography.titleSmall)
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Text(
            text = "$timeText remaining",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = CONTENT_PADDING / 2),
        )
    }
}
