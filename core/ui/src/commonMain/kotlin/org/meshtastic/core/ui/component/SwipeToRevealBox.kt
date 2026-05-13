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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.meshtastic.core.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.meshtastic.core.ui.theme.LocalReduceMotion
import kotlin.math.roundToInt

/** Anchor positions for the swipe-to-reveal gesture. */
enum class SwipeAnchor {
    /** Resting position (centered). */
    Start,

    /** Left-swipe: end actions visible. */
    RevealEnd,

    /** Right-swipe: start actions visible. */
    RevealStart,
}

/** Direction of a completed swipe action. */
enum class SwipeDirection {
    /** Swipe from start to end (right-swipe in LTR). */
    StartToEnd,

    /** Swipe from end to start (left-swipe in LTR). */
    EndToStart,
}

/**
 * State holder for [SwipeToRevealBox].
 *
 * Wraps [AnchoredDraggableState] managing the horizontal drag position and anchoring.
 */
@Stable
class SwipeToRevealState(initialAnchor: SwipeAnchor, confirmValueChange: (SwipeAnchor) -> Boolean) {
    internal val anchoredDraggableState =
        AnchoredDraggableState(
            initialValue = initialAnchor,
            confirmValueChange = confirmValueChange,
            positionalThreshold = { totalDistance -> totalDistance * POSITIONAL_THRESHOLD_FRACTION },
            velocityThreshold = { VELOCITY_THRESHOLD_DP },
            snapAnimationSpec = spring(stiffness = REVEAL_SPRING_STIFFNESS, dampingRatio = REVEAL_SPRING_DAMPING_RATIO),
            decayAnimationSpec = exponentialDecay(),
        )

    /** Current anchor position. */
    val currentValue: SwipeAnchor
        get() = anchoredDraggableState.currentValue

    /** Target anchor position (may differ from current during animation). */
    val targetValue: SwipeAnchor
        get() = anchoredDraggableState.targetValue

    /** Reset the state back to the resting position. */
    suspend fun reset() {
        anchoredDraggableState.snapTo(SwipeAnchor.Start)
    }

    companion object {
        private const val POSITIONAL_THRESHOLD_FRACTION = 0.35f
        private const val VELOCITY_THRESHOLD_DP = 400f
        private const val REVEAL_SPRING_STIFFNESS = 400f
        private const val REVEAL_SPRING_DAMPING_RATIO = 0.8f
    }
}

/**
 * Creates and remembers a [SwipeToRevealState].
 *
 * @param initialAnchor Starting anchor position.
 * @param confirmValueChange Optional callback to confirm anchor changes.
 */
@Composable
fun rememberSwipeToRevealState(
    initialAnchor: SwipeAnchor = SwipeAnchor.Start,
    confirmValueChange: (SwipeAnchor) -> Boolean = { true },
): SwipeToRevealState = remember { SwipeToRevealState(initialAnchor, confirmValueChange) }

/**
 * A composable container that reveals action content behind the foreground when the user swipes horizontally. Supports
 * bi-directional swipe with spring-physics animations.
 *
 * @param state The [SwipeToRevealState] controlling the drag position.
 * @param startContent Content revealed on right-swipe (e.g., "request position").
 * @param endContent Content revealed on left-swipe (e.g., "mute").
 * @param modifier Modifier applied to the container.
 * @param enableStartSwipe Whether right-swipe is enabled.
 * @param enableEndSwipe Whether left-swipe is enabled.
 * @param startActionLabel Accessibility label for the start (right-swipe) action.
 * @param endActionLabel Accessibility label for the end (left-swipe) action.
 * @param onActionTrigger Callback when a full-swipe threshold is crossed.
 * @param content The foreground content (the list item).
 */
@Composable
fun SwipeToRevealBox(
    state: SwipeToRevealState,
    startContent: @Composable BoxScope.() -> Unit,
    endContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    enableStartSwipe: Boolean = true,
    enableEndSwipe: Boolean = true,
    startActionLabel: String? = null,
    endActionLabel: String? = null,
    onActionTrigger: (SwipeDirection) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val reduceMotion = LocalReduceMotion.current
    val currentOnAction by rememberUpdatedState(onActionTrigger)

    val revealWidthPx = with(density) { REVEAL_WIDTH.toPx() }

    val anchors = rememberAnchors(enableStartSwipe, enableEndSwipe, revealWidthPx)
    LaunchedEffect(anchors) { state.anchoredDraggableState.updateAnchors(anchors) }

    SwipeActionEffect(state, reduceMotion) { currentOnAction(it) }

    val a11yActions =
        rememberAccessibilityActions(
            enableStartSwipe,
            enableEndSwipe,
            startActionLabel,
            endActionLabel,
            currentOnAction,
        )

    SwipeToRevealLayout(
        state = state,
        a11yActions = a11yActions,
        enableStartSwipe = enableStartSwipe,
        enableEndSwipe = enableEndSwipe,
        startContent = startContent,
        endContent = endContent,
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun SwipeActionEffect(state: SwipeToRevealState, reduceMotion: Boolean, onAction: (SwipeDirection) -> Unit) {
    val currentOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(state.targetValue) {
        when (state.targetValue) {
            SwipeAnchor.RevealStart -> {
                currentOnAction(SwipeDirection.StartToEnd)
                if (reduceMotion) state.anchoredDraggableState.snapTo(SwipeAnchor.Start)
            }

            SwipeAnchor.RevealEnd -> {
                currentOnAction(SwipeDirection.EndToStart)
                if (reduceMotion) state.anchoredDraggableState.snapTo(SwipeAnchor.Start)
            }

            SwipeAnchor.Start -> {
                /* resting */
            }
        }
    }
}

@Composable
private fun rememberAnchors(enableStart: Boolean, enableEnd: Boolean, revealWidthPx: Float) =
    remember(enableStart, enableEnd, revealWidthPx) {
        DraggableAnchors {
            SwipeAnchor.Start at 0f
            if (enableStart) SwipeAnchor.RevealStart at revealWidthPx
            if (enableEnd) SwipeAnchor.RevealEnd at -revealWidthPx
        }
    }

@Composable
private fun rememberAccessibilityActions(
    enableStart: Boolean,
    enableEnd: Boolean,
    startLabel: String?,
    endLabel: String?,
    onAction: (SwipeDirection) -> Unit,
) = remember(enableStart, enableEnd, startLabel, endLabel, onAction) {
    buildList {
        if (enableStart && startLabel != null) {
            add(
                CustomAccessibilityAction(startLabel) {
                    onAction(SwipeDirection.StartToEnd)
                    true
                },
            )
        }
        if (enableEnd && endLabel != null) {
            add(
                CustomAccessibilityAction(endLabel) {
                    onAction(SwipeDirection.EndToStart)
                    true
                },
            )
        }
    }
}

@Composable
private fun SwipeToRevealLayout(
    state: SwipeToRevealState,
    a11yActions: List<CustomAccessibilityAction>,
    enableStartSwipe: Boolean,
    enableEndSwipe: Boolean,
    startContent: @Composable BoxScope.() -> Unit,
    endContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.semantics { if (a11yActions.isNotEmpty()) customActions = a11yActions }) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            if (enableStartSwipe) startContent()
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            if (enableEndSwipe) endContent()
        }
        val offsetPx = state.anchoredDraggableState.offset.takeIf { !it.isNaN() } ?: 0f
        Box(
            modifier =
            Modifier.offset { IntOffset(offsetPx.roundToInt(), 0) }
                .anchoredDraggable(state = state.anchoredDraggableState, orientation = Orientation.Horizontal),
        ) {
            content()
        }
    }
}

/**
 * Composable modifier-factory that applies a one-shot edge-peek animation to hint at swipe availability.
 *
 * @param enabled Whether the hint should animate (false = no-op).
 * @param peekDistance The distance to peek.
 * @param onHintFinish Callback after hint animation completes.
 */
@Suppress("MagicNumber")
@Composable
fun swipeHintModifier(enabled: Boolean, peekDistance: Dp = 24.dp, onHintFinish: () -> Unit = {}): Modifier {
    val reduceMotion = LocalReduceMotion.current
    var animationTarget by remember { mutableStateOf(0.dp) }
    var hintCompleted by remember { mutableStateOf(false) }
    val currentOnHintFinish by rememberUpdatedState(onHintFinish)

    val offsetDp by
        animateDpAsState(
            targetValue = animationTarget,
            animationSpec =
            spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "swipeHint",
        )

    LaunchedEffect(enabled, reduceMotion) {
        if (!enabled || hintCompleted || reduceMotion) return@LaunchedEffect
        animationTarget = -peekDistance
        delay(HINT_HOLD_DURATION_MS)
        animationTarget = 0.dp
        delay(HINT_RETURN_DELAY_MS)
        hintCompleted = true
        currentOnHintFinish()
    }

    return if (enabled && !reduceMotion && !hintCompleted) Modifier.offset(x = offsetDp) else Modifier
}

/** Width of the revealed action area. */
private val REVEAL_WIDTH = 80.dp

private const val HINT_HOLD_DURATION_MS = 1000L
private const val HINT_RETURN_DELAY_MS = 400L
