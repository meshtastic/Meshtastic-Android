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
package org.meshtastic.feature.firmware.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.chirpy_hop_back_to_update
import org.meshtastic.core.resources.chirpy_hop_controls
import org.meshtastic.core.resources.chirpy_hop_play
import org.meshtastic.core.resources.chirpy_hop_update_complete
import org.meshtastic.core.resources.chirpy_hop_update_interrupted
import org.meshtastic.core.ui.icon.ArrowBack
import org.meshtastic.core.ui.icon.CheckCircle
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PlayArrow
import org.meshtastic.core.ui.icon.Warning
import org.meshtastic.core.ui.util.KeepScreenOn
import kotlin.math.roundToInt

private const val PERCENT = 100

/** Offers the game from an update's progress view. */
@Composable
fun ChirpyHopPlayButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = onClick, modifier = modifier) {
        Icon(MeshtasticIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(Res.string.chirpy_hop_play))
    }
}

/**
 * Chirpy Hop over the update screen, with the update's progress kept in view. When the update finishes or fails the run
 * freezes and a card sends the user back to the update screen. Back closes the game, never the update.
 */
@Composable
fun ChirpyHopDialog(status: ChirpyHopUpdateStatus, bestScore: Int, onScore: (Int) -> Unit, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        // The dialog is its own window, so the update screen's keep-screen-on flag does not reach it.
        KeepScreenOn(status.phase == ChirpyHopUpdatePhase.Active)
        ChirpyHopContent(status = status, bestScore = bestScore, onScore = onScore, onClose = onClose)
    }
}

/** The game screen inside [ChirpyHopDialog]: status band, play field and controls hint. */
@Composable
internal fun ChirpyHopContent(
    status: ChirpyHopUpdateStatus,
    bestScore: Int,
    onScore: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = status.phase == ChirpyHopUpdatePhase.Active
    Surface(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            ChirpyStatusBand(status = status, onClose = onClose)
            Box(Modifier.weight(1f).fillMaxWidth().background(ChirpyPaper)) {
                ChirpyHopPlayfield(
                    running = active,
                    bestScore = bestScore,
                    onScore = onScore,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!active) {
                    ChirpyFinishedCard(
                        status = status,
                        onClose = onClose,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Text(
                text = stringResource(Res.string.chirpy_hop_controls),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier =
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .heightIn(min = 48.dp)
                    .padding(vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun ChirpyStatusBand(status: ChirpyHopUpdateStatus, onClose: () -> Unit) {
    Row(
        modifier =
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(MeshtasticIcons.ArrowBack, contentDescription = stringResource(Res.string.chirpy_hop_back_to_update))
        }
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = status.message.asString(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                status.progress?.let { progress ->
                    Text(
                        text = "${(progress * PERCENT).roundToInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            val barColor =
                if (status.phase == ChirpyHopUpdatePhase.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            val progress = status.progress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, color = barColor, modifier = Modifier.fillMaxWidth())
            } else if (status.phase == ChirpyHopUpdatePhase.Active) {
                LinearProgressIndicator(color = barColor, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { 0f }, color = barColor, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ChirpyFinishedCard(status: ChirpyHopUpdateStatus, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val complete = status.phase == ChirpyHopUpdatePhase.Complete
    ElevatedCard(modifier = modifier.padding(24.dp).widthIn(max = 320.dp)) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (complete) MeshtasticIcons.CheckCircle else MeshtasticIcons.Warning,
                contentDescription = null,
                tint = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text =
                stringResource(
                    if (complete) {
                        Res.string.chirpy_hop_update_complete
                    } else {
                        Res.string.chirpy_hop_update_interrupted
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = status.message.asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onClose) { Text(stringResource(Res.string.chirpy_hop_back_to_update)) }
        }
    }
}
