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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.settings.components.SettingsItem

private const val COOL_DOWN_TIME_MS = 30000L

@Composable
fun TracerouteButton(
    text: String = stringResource(id = R.string.traceroute),
    lastTracerouteTime: Long?,
    onClick: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    var isCoolingDown by remember { mutableStateOf(false) }

    LaunchedEffect(lastTracerouteTime) {
        val timeSinceLast = System.currentTimeMillis() - (lastTracerouteTime ?: 0)
        isCoolingDown = timeSinceLast < COOL_DOWN_TIME_MS

        if (isCoolingDown) {
            val remainingTime = COOL_DOWN_TIME_MS - timeSinceLast
            progress.snapTo(remainingTime / COOL_DOWN_TIME_MS.toFloat())
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = remainingTime.toInt(), easing = { it }),
            )
            isCoolingDown = false
        }
    }

    SettingsItem(
        text = text,
        enabled = !isCoolingDown,
        leadingIcon = Icons.Default.Route,
        trailingContent = {
            if (isCoolingDown) {
                CircularProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    trackColor = ProgressIndicatorDefaults.circularDeterminateTrackColor,
                    strokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
                )
            }
        },
        onClick = {
            if (!isCoolingDown) {
                onClick()
            }
        },
    )
}
