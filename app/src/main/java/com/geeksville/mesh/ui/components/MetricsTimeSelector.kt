/* Inspired by https://gist.github.com/zach-klippenstein/7ae8874db304f957d6bb91263e292117 */

package com.geeksville.mesh.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow.Companion.Ellipsis
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.model.TimeFrame

private const val NO_OPTION_INDEX = -1

private val TRACK_PADDING = 2.dp

private val TRACK_COLOR = Color.LightGray.copy(alpha = .5f)

private val PRESSED_TRACK_PADDING = 1.dp

private val OPTION_PADDING = 5.dp

private const val PRESSED_UNSELECTED_ALPHA = .6f

private val BACKGROUND_SHAPE = RoundedCornerShape(8.dp)

/**
 * Provides the user with a set of time options they can choose from that controls
 * the time frame the data being plotted was received.
 */
@Composable
fun MetricsTimeSelector(
    selectedTime: TimeFrame,
    onOptionSelected: (TimeFrame) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (TimeFrame) -> Unit
) {
    val state = remember { TimeSelectorState() }
    state.selectedOption = state.options.indexOf(selectedTime)
    state.onOptionSelected = { onOptionSelected(state.options[it]) }

    /* Animate between whole-number indices so we don't need to do pixel calculations. */
    val selectedIndexOffset by animateFloatAsState(state.selectedOption.toFloat(), label = "Selected Index Offset")

    Layout(
        content = {
            SelectedIndicator(state)
            Dividers(state)
            TimeOptions(state, content)
        },
        modifier = modifier
            .fillMaxWidth()
            .then(state.inputModifier)
            .background(TRACK_COLOR, BACKGROUND_SHAPE)
            .padding(TRACK_PADDING)
    ) { measurables, constraints ->
        val (indicatorMeasurable, dividersMeasurable, optionsMeasurable) = measurables

        /* Measure the options first so we know how tall to make the indicator. */
        val optionsPlaceable = optionsMeasurable.measure(constraints)
        state.updatePressedScale(optionsPlaceable.height, this)

        /* Measure the indicator and dividers to be the right size. */
        val indicatorPlaceable = indicatorMeasurable.measure(
            Constraints.fixed(
                width = optionsPlaceable.width / state.options.size,
                height = optionsPlaceable.height
            )
        )

        val dividersPlaceable = dividersMeasurable.measure(
            Constraints.fixed(
                width = optionsPlaceable.width,
                height = optionsPlaceable.height
            )
        )

        layout(optionsPlaceable.width, optionsPlaceable.height) {
            val optionWidth = optionsPlaceable.width / state.options.size

            /* Place the indicator first so that it's below the option labels. */
            indicatorPlaceable.placeRelative(
                x = (selectedIndexOffset * optionWidth).toInt(),
                y = 0
            )
            dividersPlaceable.placeRelative(IntOffset.Zero)
            optionsPlaceable.placeRelative(IntOffset.Zero)
        }
    }
}

/**
 * Visual representation of the time option the user may select.
 */
@Composable
fun TimeLabel(text: String) {
    Text(text, maxLines = 1, overflow = Ellipsis)
}

/**
 * Draws the selected indicator on the [MetricsTimeSelector] track.
 */
@Composable
private fun SelectedIndicator(state: TimeSelectorState) {
    Box(
        Modifier
            .then(
                state.optionScaleModifier(
                    pressed = state.pressedOption == state.selectedOption,
                    option = state.selectedOption
                )
            )
            .shadow(4.dp, BACKGROUND_SHAPE)
            .background(MaterialTheme.colors.background, BACKGROUND_SHAPE)
    )
}

/**
 * Draws dividers between [TimeLabel]s.
 */
@Composable
private fun Dividers(state: TimeSelectorState) {
    /* Animate each divider independently. */
    val alphas = (0 until state.options.size).map { i ->
        val selectionAdjacent = i == state.selectedOption || i - 1 == state.selectedOption
        animateFloatAsState(if (selectionAdjacent) 0f else 1f, label = "Dividers")
    }

    Canvas(Modifier.fillMaxSize()) {
        val optionWidth = size.width / state.options.size
        val dividerPadding = TRACK_PADDING + PRESSED_TRACK_PADDING

        alphas.forEachIndexed { i, alpha ->
            val x = i * optionWidth
            drawLine(
                Color.White,
                alpha = alpha.value,
                start = Offset(x, dividerPadding.toPx()),
                end = Offset(x, size.height - dividerPadding.toPx())
            )
        }
    }
}

/**
 * Draws the time options available to the user.
 */
@Composable
private fun TimeOptions(
    state: TimeSelectorState,
    content: @Composable (TimeFrame) -> Unit
) {
    CompositionLocalProvider(
        LocalTextStyle provides TextStyle(fontWeight = FontWeight.Medium)
    ) {
        Row(
            horizontalArrangement = spacedBy(TRACK_PADDING),
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
        ) {
            state.options.forEachIndexed { i, timeFrame ->
                val isSelected = i == state.selectedOption
                val isPressed = i == state.pressedOption

                /* Unselected presses are represented by fading. */
                val alpha by animateFloatAsState(
                    if (!isSelected && isPressed) PRESSED_UNSELECTED_ALPHA else 1f,
                    label = "Unselected"
                )

                val semanticsModifier = Modifier.semantics(mergeDescendants = true) {
                    selected = isSelected
                    role = Role.Button
                    onClick { state.onOptionSelected(i); true }
                    stateDescription = if (isSelected) "Selected" else "Not selected"
                }

                Box(
                    Modifier
                        /* Divide space evenly between all options. */
                        .weight(1f)
                        .then(semanticsModifier)
                        .padding(OPTION_PADDING)
                        /* Draw pressed indication when not selected. */
                        .alpha(alpha)
                        /* Selected presses are represented by scaling. */
                        .then(state.optionScaleModifier(isPressed && isSelected, i))
                        /* Center the option content. */
                        .wrapContentWidth()
                ) {
                    content(timeFrame)
                }
            }
        }
    }
}

/**
 * Contains and handles the state necessary to present the [MetricsTimeSelector] to the user.
 */
private class TimeSelectorState {
    val options = TimeFrame.entries.toTypedArray()
    var selectedOption by mutableIntStateOf(0)
    var onOptionSelected: (Int) -> Unit by mutableStateOf({})
    var pressedOption by mutableIntStateOf(NO_OPTION_INDEX)

    /**
     * Scale factor that should be used to scale pressed option. When this scale is applied,
     * exactly [PRESSED_TRACK_PADDING] will be added around the element's usual size.
     */
    var pressedSelectedScale by mutableFloatStateOf(1f)
        private set

    /**
     * Calculates the scale factor we need to use for pressed options to get the desired padding.
     */
    fun updatePressedScale(controlHeight: Int, density: Density) {
        with(density) {
            val pressedPadding = PRESSED_TRACK_PADDING * 2
            val pressedHeight = controlHeight - pressedPadding.toPx()
            pressedSelectedScale = pressedHeight / controlHeight
        }
    }

    /**
     * Returns a [Modifier] that will scale an element so that it gets [PRESSED_TRACK_PADDING] extra
     * padding around it. The scale will be animated.
     *
     * The scale is also performed around either the left or right edge of the element if the option
     * is the first or last option, respectively. In those cases, the scale will also be translated so
     * that [PRESSED_TRACK_PADDING] will be added on the left or right edge.
     */
    @SuppressLint("ModifierFactoryExtensionFunction")
    fun optionScaleModifier(
        pressed: Boolean,
        option: Int,
    ): Modifier = Modifier.composed {
        val scale by animateFloatAsState(if (pressed) pressedSelectedScale else 1f, label = "Scale")
        val xOffset by animateDpAsState(if (pressed) PRESSED_TRACK_PADDING else 0.dp, label = "x Offset")

        graphicsLayer {
            this.scaleX = scale
            this.scaleY = scale

            /* Scales on the ends should gravitate to that edge. */
            this.transformOrigin = TransformOrigin(
                pivotFractionX = when (option) {
                    0 -> 0f
                    options.size - 1 -> 1f
                    else -> .5f
                },
                pivotFractionY = .5f
            )

            /* But should still move inwards to keep the pressed padding consistent with top and bottom. */
            this.translationX = when (option) {
                0 -> xOffset.toPx()
                options.size - 1 -> -xOffset.toPx()
                else -> 0f
            }
        }
    }

    /**
     * A [Modifier] that will listen for touch gestures and update the selected and pressed properties
     * of this state appropriately.
     */
    val inputModifier = Modifier.pointerInput(options.size) {
        val optionWidth = size.width / options.size

        /* Helper to calculate which option an event occurred in. */
        fun optionIndex(change: PointerInputChange): Int =
            ((change.position.x / size.width.toFloat()) * options.size)
                .toInt()
                .coerceIn(0, options.size - 1)

        awaitEachGesture {
            val down = awaitFirstDown()

            pressedOption = optionIndex(down)
            val downOnSelected = pressedOption == selectedOption
            val optionBounds = Rect(
                left = pressedOption * optionWidth.toFloat(),
                right = (pressedOption + 1) * optionWidth.toFloat(),
                top = 0f,
                bottom = size.height.toFloat()
            )

            if (downOnSelected) {
                horizontalDrag(down.id) { change ->
                    pressedOption = optionIndex(change)

                    if (pressedOption != selectedOption) {
                        onOptionSelected(pressedOption)
                    }
                }
            } else {
                waitForUpOrCancellation(inBounds = optionBounds)
                    /* Null means the gesture was cancelled (e.g. dragged out of bounds). */
                    ?.let { onOptionSelected(pressedOption) }
            }
            pressedOption = NO_OPTION_INDEX
        }
    }
}

/**
 * Works with bounds that may not be at 0,0.
 */
@Suppress("ReturnCount")
private suspend fun AwaitPointerEventScope.waitForUpOrCancellation(inBounds: Rect): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        if (event.changes.all { it.changedToUp() }) {
            /* All pointers are up */
            return event.changes[0]
        }

        if (event.changes.any { it.isConsumed || !inBounds.contains(it.position) }) {
            /* Canceled */
            return null
        }

        val consumeCheck = awaitPointerEvent(PointerEventPass.Final)
        if (consumeCheck.changes.any { it.isConsumed }) {
            return null
        }
    }
}

@Preview
@Composable
fun MetricsTimeSelectorPreview() {
    MaterialTheme {
        Surface {
            Column(Modifier.padding(8.dp)) {

                var selectedOption by remember { mutableStateOf(TimeFrame.TWENTY_FOUR_HOURS) }
                MetricsTimeSelector(
                    selectedOption,
                    onOptionSelected = { selectedOption = it }
                ) {
                    TimeLabel(stringResource(it.strRes))
                }
            }
        }
    }
}
