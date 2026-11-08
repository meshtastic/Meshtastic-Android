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
package org.meshtastic.feature.node.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.meshtastic.core.common.util.nowMillis

internal const val COOL_DOWN_TIME_MS = 30000L
internal const val REQUEST_NEIGHBORS_COOL_DOWN_TIME_MS = 180000L // 3 minutes

@Composable
@NonRestartableComposable
fun CooldownIconButton(
    onClick: () -> Unit,
    cooldownTimestamp: Long?,
    modifier: Modifier = Modifier,
    cooldownDuration: Long = COOL_DOWN_TIME_MS,
    content: @Composable () -> Unit,
) = CooldownBaseButton(
    onClick = onClick,
    cooldownTimestamp = cooldownTimestamp,
    cooldownDuration = cooldownDuration,
    modifier = modifier,
    outlined = false,
    content = content,
)

@Composable
@NonRestartableComposable
fun CooldownOutlinedIconButton(
    onClick: () -> Unit,
    cooldownTimestamp: Long?,
    modifier: Modifier = Modifier,
    cooldownDuration: Long = COOL_DOWN_TIME_MS,
    content: @Composable () -> Unit,
) {
    CooldownBaseButton(
        onClick = onClick,
        cooldownTimestamp = cooldownTimestamp,
        cooldownDuration = cooldownDuration,
        modifier = modifier,
        outlined = true,
        content = content,
    )
}

private const val TICK = 100L
private const val MS_PER_SECOND = 1000L

/** Whole seconds left until [endTime], rounded up so the last second still reads 1; 0 once it has passed. */
internal fun cooldownSecondsLeft(endTime: Long): Long =
    ((endTime - nowMillis).coerceAtLeast(0L) + MS_PER_SECOND - 1) / MS_PER_SECOND

@Composable
private fun CooldownBaseButton(
    onClick: () -> Unit,
    cooldownTimestamp: Long?,
    cooldownDuration: Long,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    content: @Composable () -> Unit,
) {
    var progress by remember { mutableStateOf(0f) }
    var remainingSeconds by remember { mutableLongStateOf(0L) }
    var isCoolingDown by remember { mutableStateOf(false) }

    LaunchedEffect(cooldownTimestamp, cooldownDuration) {
        val endTime = (cooldownTimestamp ?: 0L) + cooldownDuration
        isCoolingDown = nowMillis < endTime

        while (isCoolingDown) {
            val remainingTime = endTime - nowMillis
            if (remainingTime <= 0) break
            progress = (remainingTime.toFloat() / cooldownDuration).coerceIn(0f, 1f)
            remainingSeconds = cooldownSecondsLeft(endTime)
            delay(TICK)
            isCoolingDown = nowMillis < endTime
        }
        progress = 0f
        isCoolingDown = false
    }

    val buttonContent: @Composable () -> Unit = {
        if (isCoolingDown) {
            // The seconds left say when the radio will take the next request, before it would refuse one.
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(28.dp),
                    strokeCap = StrokeCap.Round,
                )
                Text(text = remainingSeconds.toString(), style = MaterialTheme.typography.labelSmall)
            }
        } else {
            content()
        }
    }

    if (outlined) {
        OutlinedIconButton(
            onClick = onClick,
            enabled = !isCoolingDown,
            colors = IconButtonDefaults.outlinedIconButtonColors(),
            modifier = modifier,
            content = buttonContent,
        )
    } else {
        IconButton(
            onClick = onClick,
            enabled = !isCoolingDown,
            colors = IconButtonDefaults.iconButtonColors(),
            modifier = modifier,
            content = buttonContent,
        )
    }
}
