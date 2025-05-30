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

package com.geeksville.mesh.ui.common.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

// Derived in part from: https://github.com/androidx/androidx/blob/c92ad2941368202b2d78b8d14c71bf81e9525944/compose/foundation/foundation/integration-tests/foundation-demos/src/main/java/androidx/compose/foundation/demos/LazyColumnDragAndDropDemo.kt
@Preview
@Composable
fun LazyColumnDragAndDropDemo() {
    var list by remember { mutableStateOf(List(50) { it }) }

    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(listState, headerCount = 1) { fromIndex, toIndex ->
        if (fromIndex in list.indices && toIndex in list.indices) {
            list = list.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    LazyColumn(
        modifier = Modifier.dragContainer(
            dragDropState = dragDropState,
            haptics = LocalHapticFeedback.current,
        ),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Header", Modifier.fillMaxWidth().padding(20.dp))
        }

        itemsIndexed(list, key = { _, item -> item }) { index, item ->
            DraggableItem(dragDropState, index + 1) { isDragging ->
                Card {
                    Text("Item $item", Modifier.fillMaxWidth().padding(20.dp))
                }
            }
        }

        item {
            Text("Footer", Modifier.fillMaxWidth().padding(20.dp))
        }
    }
}

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    headerCount: Int = 0,
    onMove: (Int, Int) -> Unit
): DragDropState {
    val scope = rememberCoroutineScope()
    val state = remember(lazyListState) { DragDropState(lazyListState, headerCount, scope, onMove) }
    LaunchedEffect(state) {
        while (true) {
            val diff = state.scrollChannel.receive()
            lazyListState.scrollBy(diff)
        }
    }
    return state
}

class DragDropState
internal constructor(
    private val state: LazyListState,
    private val headerCount: Int,
    private val scope: CoroutineScope,
    private val onMove: (Int, Int) -> Unit
) {
    private var draggingItemIndex by mutableStateOf<Int?>(null)
    val adjustedItemIndex get() = draggingItemIndex?.minus(headerCount)

    internal val scrollChannel = Channel<Float>()

    private var draggingItemDraggedDelta by mutableFloatStateOf(0f)
    private var draggingItemInitialOffset by mutableIntStateOf(0)
    internal val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item ->
            draggingItemInitialOffset + draggingItemDraggedDelta - item.offset
        } ?: 0f

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    internal var previousIndexOfDraggedItem by mutableStateOf<Int?>(null)
        private set

    internal var previousItemOffset = Animatable(0f)
        private set

    internal fun onDragStart(offset: Offset): LazyListItemInfo? = state.layoutInfo.visibleItemsInfo
        .filter { it.contentType == DragDropContentType }
        .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
        ?.also {
            draggingItemIndex = it.index
            draggingItemInitialOffset = it.offset
        }

    internal fun onDragInterrupted() {
        if (draggingItemIndex != null) {
            previousIndexOfDraggedItem = draggingItemIndex
            val startOffset = draggingItemOffset
            scope.launch {
                previousItemOffset.snapTo(startOffset)
                previousItemOffset.animateTo(
                    0f,
                    spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 1f)
                )
                previousIndexOfDraggedItem = null
            }
        }
        draggingItemDraggedDelta = 0f
        draggingItemIndex = null
        draggingItemInitialOffset = 0
    }

    internal fun onDrag(offset: Offset) {
        draggingItemDraggedDelta += offset.y

        val draggingItem = draggingItemLayoutInfo ?: return
        val startOffset = draggingItem.offset + draggingItemOffset
        val endOffset = startOffset + draggingItem.size
        val middleOffset = startOffset + (endOffset - startOffset) / 2f

        val targetItem = state.layoutInfo.visibleItemsInfo
            .find { item ->
                middleOffset.toInt() in item.offset..item.offsetEnd &&
                        draggingItem.index != item.index
            }
        if (targetItem != null) {
            if (
                draggingItem.index == state.firstVisibleItemIndex ||
                targetItem.index == state.firstVisibleItemIndex
            ) {
                state.requestScrollToItem(
                    state.firstVisibleItemIndex,
                    state.firstVisibleItemScrollOffset
                )
            }
            onMove.invoke(draggingItem.index - headerCount, targetItem.index - headerCount)
            draggingItemIndex = targetItem.index
        } else {
            val overscroll = when {
                draggingItemDraggedDelta > 0 ->
                    (endOffset - state.layoutInfo.viewportEndOffset).coerceAtLeast(0f)

                draggingItemDraggedDelta < 0 ->
                    (startOffset - state.layoutInfo.viewportStartOffset).coerceAtMost(0f)

                else -> 0f
            }
            if (overscroll != 0f) {
                scrollChannel.trySend(overscroll)
            }
        }
    }

    private val LazyListItemInfo.offsetEnd: Int
        get() = this.offset + this.size
}

fun Modifier.dragContainer(
    dragDropState: DragDropState,
    haptics: HapticFeedback,
): Modifier {
    return this.pointerInput(dragDropState) {
        detectDragGesturesAfterLongPress(
            onDrag = { change, offset ->
                change.consume()
                dragDropState.onDrag(offset = offset)
            },
            onDragStart = { offset ->
                dragDropState.onDragStart(offset) ?: return@detectDragGesturesAfterLongPress
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            onDragEnd = { dragDropState.onDragInterrupted() },
            onDragCancel = { dragDropState.onDragInterrupted() }
        )
    }
}

@Composable
fun LazyItemScope.DraggableItem(
    dragDropState: DragDropState,
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(isDragging: Boolean) -> Unit
) {
    val dragging = index == dragDropState.adjustedItemIndex
    val draggingModifier = if (dragging) {
        Modifier
            .zIndex(1f)
            .graphicsLayer { translationY = dragDropState.draggingItemOffset }
    } else if (index == dragDropState.previousIndexOfDraggedItem) {
        Modifier
            .zIndex(1f)
            .graphicsLayer { translationY = dragDropState.previousItemOffset.value }
    } else {
        Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
    }
    Column(modifier = modifier.then(draggingModifier)) { content(dragging) }
}

const val DragDropContentType = "drag-and-drop"

/**
 * Extension function for [LazyListScope] with drag-and-drop functionality for indexed items.
 *
 * Wraps [itemsIndexed] function with [detectDragGesturesAfterLongPress] to enable long-press
 * drag gestures and allow items in the list to be reordered using the provided [DragDropState].
 */
inline fun <T> LazyListScope.dragDropItemsIndexed(
    items: List<T>,
    dragDropState: DragDropState,
    noinline key: ((index: Int, item: T) -> Any)? = null,
    crossinline itemContent: @Composable LazyItemScope.(index: Int, item: T, isDragging: Boolean) -> Unit
) = itemsIndexed(
    items = items,
    key = key,
    contentType = { _, _ -> DragDropContentType },
    itemContent = { index, item ->
        DraggableItem(
            dragDropState = dragDropState,
            index = index,
            content = { isDragging -> itemContent(index, item, isDragging) }
        )
    }
)
