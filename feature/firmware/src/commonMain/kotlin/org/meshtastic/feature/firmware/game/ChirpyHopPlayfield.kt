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

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.chirpy_hop
import org.meshtastic.core.resources.chirpy_hop_best
import org.meshtastic.core.resources.chirpy_hop_controls
import org.meshtastic.core.resources.chirpy_hop_game_over
import org.meshtastic.core.resources.chirpy_hop_play_again
import org.meshtastic.core.resources.chirpy_hop_tap_to_start
import org.meshtastic.core.resources.img_chirpy
import org.meshtastic.core.resources.img_chirpy_hop_crouch
import org.meshtastic.core.resources.img_chirpy_hop_idle
import org.meshtastic.core.resources.img_chirpy_hop_jump
import org.meshtastic.core.resources.img_chirpy_hop_run_1
import org.meshtastic.core.resources.img_chirpy_hop_run_2
import org.meshtastic.core.resources.img_chirpy_hop_run_3
import org.meshtastic.core.resources.img_chirpy_hop_run_4
import org.meshtastic.core.resources.img_chirpy_hop_run_5
import org.meshtastic.core.resources.img_chirpy_hop_run_6
import org.meshtastic.core.resources.img_chirpy_hop_run_7
import org.meshtastic.core.resources.img_chirpy_hop_run_8
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh

private val ChirpyMutedInk = Color(0xFF7A7A7A)

private const val CROUCH_DRAG_DP = 18
private const val FIRST_FRAME_SECONDS = 1.0 / 60.0
private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val SCORE_DIGITS = 5

// Just above centre, where the iOS scene puts its prompts.
private val PromptAlignment = BiasAlignment(0f, -0.04f)

/** Chirpy's art: the raster sprites he runs with, and the design vector he is drawn from once knocked out. */
internal class ChirpyHopSprites(
    val run: List<ImageBitmap>,
    val jump: ImageBitmap,
    val idle: ImageBitmap,
    val crouch: ImageBitmap,
    val dazed: Painter,
)

@Composable
private fun rememberChirpyHopSprites(): ChirpyHopSprites {
    val run =
        listOf(
            imageResource(Res.drawable.img_chirpy_hop_run_1),
            imageResource(Res.drawable.img_chirpy_hop_run_2),
            imageResource(Res.drawable.img_chirpy_hop_run_3),
            imageResource(Res.drawable.img_chirpy_hop_run_4),
            imageResource(Res.drawable.img_chirpy_hop_run_5),
            imageResource(Res.drawable.img_chirpy_hop_run_6),
            imageResource(Res.drawable.img_chirpy_hop_run_7),
            imageResource(Res.drawable.img_chirpy_hop_run_8),
        )
    val jump = imageResource(Res.drawable.img_chirpy_hop_jump)
    val idle = imageResource(Res.drawable.img_chirpy_hop_idle)
    val crouch = imageResource(Res.drawable.img_chirpy_hop_crouch)
    val dazed = rememberVectorPainter(vectorResource(Res.drawable.img_chirpy))
    return remember(run, jump, idle, crouch, dazed) { ChirpyHopSprites(run, jump, idle, crouch, dazed) }
}

/**
 * The game surface: tap to jump, drag down to crouch. [running] is false once the update stops, which freezes the run
 * in place and ignores input. [onScore] reports every point so the caller can keep the best score.
 */
@Composable
internal fun ChirpyHopPlayfield(
    running: Boolean,
    bestScore: Int,
    onScore: (Int) -> Unit,
    modifier: Modifier = Modifier,
    engine: ChirpyHopEngine = remember { ChirpyHopEngine() },
) {
    val state = remember(engine) { ChirpyHopState(engine) }
    val sprites = rememberChirpyHopSprites()
    val haptics = LocalHapticFeedback.current
    val currentOnScore by rememberUpdatedState(onScore)

    // Frames are requested only while something moves: during a run, and after a knockout while Chirpy sways. A game
    // waiting for its first tap draws once and costs nothing.
    LaunchedEffect(state, running, state.animating) {
        if (!running) {
            state.freeze()
            return@LaunchedEffect
        }
        if (!state.animating) return@LaunchedEffect
        var lastNanos = 0L
        while (isActive) {
            withFrameNanos { now ->
                val delta = if (lastNanos == 0L) FIRST_FRAME_SECONDS else (now - lastNanos) / NANOS_PER_SECOND
                lastNanos = now
                when (state.step(delta)) {
                    ChirpyHopEvent.Scored -> {
                        currentOnScore(state.score)
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }

                    ChirpyHopEvent.KnockedOut -> haptics.performHapticFeedback(HapticFeedbackType.Reject)

                    ChirpyHopEvent.None -> Unit
                }
            }
        }
    }

    // Remembered so recomposition (a new score, say) keeps the cached scene; it is rebuilt only when the size changes.
    val sceneModifier =
        remember(state, sprites) {
            Modifier.fillMaxSize().drawWithCache {
                val scene = ChirpyHopScene(size, this)
                onDrawBehind {
                    // Reading the tick here invalidates only the draw phase on every frame, never composition.
                    state.frameTick.longValue
                    with(scene) { drawScene(state.engine, state.world, sprites) }
                }
            }
        }

    val controls =
        Modifier.chirpyHopControls(
            running = running,
            label = stringResource(Res.string.chirpy_hop),
            controls = stringResource(Res.string.chirpy_hop_controls),
            onCrouch = state::setCrouching,
            onTap = { if (state.tap()) haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap) },
        )
    Box(modifier = modifier.then(controls)) {
        Spacer(sceneModifier)
        ChirpyHud(phase = state.hudPhase, score = state.score, bestScore = maxOf(bestScore, state.score))
    }
}

/**
 * Tap to jump, drag down to crouch, and an accessibility action that taps. Input is ignored once the update has
 * stopped, since the run is frozen then.
 */
private fun Modifier.chirpyHopControls(
    running: Boolean,
    label: String,
    controls: String,
    onCrouch: (Boolean) -> Unit,
    onTap: () -> Unit,
): Modifier = semantics {
    role = Role.Button
    contentDescription = label
    onClick(label = controls) {
        if (running) onTap()
        running
    }
}
    .pointerInput(running) {
        if (!running) return@pointerInput
        val crouchThreshold = CROUCH_DRAG_DP.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown()
            var crouching = false
            var change = down
            while (change.pressed) {
                change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                val shouldCrouch = change.position.y - down.position.y > crouchThreshold
                if (shouldCrouch != crouching) {
                    crouching = shouldCrouch
                    onCrouch(shouldCrouch)
                }
            }
            if (crouching) onCrouch(false) else onTap()
        }
    }

@Composable
private fun ChirpyHud(phase: ChirpyHopPhase, score: Int, bestScore: Int) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 24.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = score.toString().padStart(SCORE_DIGITS, '0'),
                color = ChirpyInk,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            )
            Text(
                text = stringResource(Res.string.chirpy_hop_best, bestScore.toString().padStart(SCORE_DIGITS, '0')),
                color = ChirpyMutedInk,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
        }
        when (phase) {
            ChirpyHopPhase.Ready ->
                Text(
                    text = stringResource(Res.string.chirpy_hop_tap_to_start),
                    color = ChirpyInk,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.align(PromptAlignment),
                )

            ChirpyHopPhase.GameOver ->
                Column(modifier = Modifier.align(PromptAlignment), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.chirpy_hop_game_over),
                        color = ChirpyInk,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                    )
                    Icon(
                        imageVector = MeshtasticIcons.Refresh,
                        contentDescription = stringResource(Res.string.chirpy_hop_play_again),
                        tint = ChirpyInk,
                        modifier = Modifier.padding(top = 8.dp).size(28.dp),
                    )
                }

            ChirpyHopPhase.Running -> Unit
        }
    }
}
