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

package org.meshtastic.core.ui.component

import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val SCROLL_TO_TOP_INDEX = 0
private const val FAST_SCROLL_THRESHOLD = 10

/**
 * Executes the smart scroll-to-top policy.
 *
 * Policy:
 * - If the first visible item is already at index 0, do nothing.
 * - Otherwise, smoothly animate the list back to the first item.
 */
fun LazyListState.smartScrollToTop(coroutineScope: CoroutineScope) {
    smartScrollToIndex(coroutineScope = coroutineScope, targetIndex = SCROLL_TO_TOP_INDEX)
}

/**
 * Scrolls to the [targetIndex] while applying the same fast-scroll optimisation used by [smartScrollToTop].
 *
 * If the destination is far away, the list first jumps closer to the goal (within [FAST_SCROLL_THRESHOLD] items) to
 * avoid long smooth animations and then animates the final segment.
 *
 * @param coroutineScope Scope used to perform the scroll operations.
 * @param targetIndex Absolute index that should end up at the top of the viewport.
 */
fun LazyListState.smartScrollToIndex(coroutineScope: CoroutineScope, targetIndex: Int) {
    if (targetIndex < 0 || firstVisibleItemIndex == targetIndex) {
        return
    }
    coroutineScope.launch {
        val totalItems = layoutInfo.totalItemsCount
        if (totalItems == 0) {
            return@launch
        }
        val clampedTarget = targetIndex.coerceIn(0, totalItems - 1)
        val difference = firstVisibleItemIndex - clampedTarget
        val jumpIndex =
            when {
                difference > FAST_SCROLL_THRESHOLD ->
                    (clampedTarget + FAST_SCROLL_THRESHOLD).coerceAtMost(totalItems - 1)
                difference < -FAST_SCROLL_THRESHOLD -> (clampedTarget - FAST_SCROLL_THRESHOLD).coerceAtLeast(0)
                else -> null
            }
        jumpIndex?.let { scrollToItem(it) }
        animateScrollToItem(index = clampedTarget)
    }
}
