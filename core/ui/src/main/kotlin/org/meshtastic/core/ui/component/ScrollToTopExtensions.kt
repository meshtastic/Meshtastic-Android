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
import kotlin.math.max

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
    if (firstVisibleItemIndex == SCROLL_TO_TOP_INDEX) {
        return
    }
    coroutineScope.launch {
        if (firstVisibleItemIndex > FAST_SCROLL_THRESHOLD) {
            val jumpIndex = max(SCROLL_TO_TOP_INDEX, firstVisibleItemIndex - FAST_SCROLL_THRESHOLD)
            scrollToItem(jumpIndex)
        }
        animateScrollToItem(index = SCROLL_TO_TOP_INDEX)
    }
}
