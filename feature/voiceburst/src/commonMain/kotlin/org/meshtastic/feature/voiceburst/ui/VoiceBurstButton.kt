/*
 * Copyright (c) 2026 Chris7X
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
package org.meshtastic.feature.voiceburst.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import org.meshtastic.feature.voiceburst.model.VoiceBurstState
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.voice_burst_record
import org.meshtastic.core.resources.voice_burst_recording
import org.meshtastic.core.resources.voice_burst_encoding
import org.meshtastic.core.resources.voice_burst_sending
import org.meshtastic.core.resources.voice_burst_sent
import org.meshtastic.core.resources.voice_burst_error
import org.meshtastic.core.resources.voice_burst_received
import org.meshtastic.core.resources.voice_burst_unsupported
import org.jetbrains.compose.resources.stringResource

/**
 * PTT (Push-To-Talk) button for Voice Burst.
 *
 * Render this composable only when Voice Burst is available; callers should not render
 * it for [VoiceBurstState.Unsupported].
 * Disabled during non-interactive processing states such as encoding and sending.
 *
 * Visual states:
 *   Idle       -> Mic icon, normal color
 *   Recording  -> Pulsing Mic icon (scale animation), error/red color
 *   Encoding   -> Mic icon, secondary color, disabled
 *   Sending    -> Mic icon, secondary color, disabled
 *   Sent       -> Mic icon, primary color (short feedback)
 *   Error      -> MicOff icon, error color
 *   Unsupported -> hidden (caller should not render the composable)
 *
 * @param state    Current state machine state
 * @param onClick  Callback when the user presses the button
 * @param modifier Optional modifier
 */
@Composable
fun VoiceBurstButton(
    state: VoiceBurstState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Enabled in Idle (start), Recording (stop), Sent (start immediately), and Error (reset)
    val isEnabled = state is VoiceBurstState.Idle
        || state is VoiceBurstState.Recording
        || state is VoiceBurstState.Sent
        || state is VoiceBurstState.Error
    val isRecording = state is VoiceBurstState.Recording
    val isError = state is VoiceBurstState.Error

    val tint by animateColorAsState(
        targetValue = when (state) {
            is VoiceBurstState.Recording -> MaterialTheme.colorScheme.error
            is VoiceBurstState.Sent      -> MaterialTheme.colorScheme.primary
            is VoiceBurstState.Error     -> MaterialTheme.colorScheme.error
            else                         -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "voiceBurstTint",
    )

    // Pulsation during recording
    val infiniteTransition = rememberInfiniteTransition(label = "recordingPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recordingScale",
    )

    IconButton(
        onClick = onClick,
        enabled = isEnabled,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Progress ring during recording: shows fraction of the max duration
            if (isRecording) {
                val progress = ((state as VoiceBurstState.Recording).elapsedMs / 1000f)
                    .coerceIn(0f, 1f)
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(36.dp),
                    color = MaterialTheme.colorScheme.error,
                    strokeWidth = 2.5.dp,
                    trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                )
            }
            Icon(
                imageVector = when {
                    isRecording                    -> Icons.Rounded.StopCircle
                    state is VoiceBurstState.Sent  -> Icons.Rounded.CheckCircle
                    isError                        -> Icons.Rounded.MicOff
                    else                           -> Icons.Rounded.Mic
                },
                contentDescription = when (state) {
                    is VoiceBurstState.Idle        -> stringResource(Res.string.voice_burst_record)
                    is VoiceBurstState.Recording   -> stringResource(Res.string.voice_burst_recording, (state.elapsedMs / 100) / 10f)
                    is VoiceBurstState.Encoding    -> stringResource(Res.string.voice_burst_encoding)
                    is VoiceBurstState.Sending,
                    is VoiceBurstState.Queued      -> stringResource(Res.string.voice_burst_sending)
                    is VoiceBurstState.Sent        -> stringResource(Res.string.voice_burst_sent)
                    is VoiceBurstState.Error       -> stringResource(Res.string.voice_burst_error)
                    is VoiceBurstState.Received    -> stringResource(Res.string.voice_burst_received)
                    is VoiceBurstState.Unsupported -> stringResource(Res.string.voice_burst_unsupported)
                },
                tint = tint,
                modifier = Modifier.size(24.dp).scale(scale),
            )
        }
    }
}
