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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape

/** Records the key of each list item as the screen declares it, so a key can be turned back into a list index. */
internal class FieldKeyIndex {
    private val indices = HashMap<Any, Int>()
    private var count = 0

    fun indexOf(key: String): Int? = indices[key]

    fun reset() {
        indices.clear()
        count = 0
    }

    fun recordItem(key: Any?) {
        if (key != null) indices.getOrPut(key) { count }
        count++
    }

    fun recordItems(size: Int, key: ((Int) -> Any)?) {
        if (key != null) repeat(size) { indices.getOrPut(key(it)) { count + it } }
        count += size
    }
}

/**
 * Forwards to [delegate] while noting each item's key in [index]. When an item's key is [highlightKey], its content is
 * drawn over a tint, fading out once [highlightActive] ends, so the control a search result named is easy to find.
 */
internal class FieldKeyRecordingScope(
    private val delegate: LazyListScope,
    private val index: FieldKeyIndex,
    private val highlightKey: String?,
    private val highlightActive: Boolean,
) : LazyListScope by delegate {

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        index.recordItem(key)
        delegate.item(key, contentType) {
            if (key != null && key == highlightKey) {
                Highlighted(highlightActive) { content() }
            } else {
                content()
            }
        }
    }

    override fun items(
        count: Int,
        key: ((Int) -> Any)?,
        contentType: (Int) -> Any?,
        itemContent: @Composable LazyItemScope.(Int) -> Unit,
    ) {
        index.recordItems(count, key)
        delegate.items(count, key, contentType) { i ->
            if (key != null && key(i) == highlightKey) {
                Highlighted(highlightActive) { itemContent(i) }
            } else {
                itemContent(i)
            }
        }
    }
}

@Composable
private fun Highlighted(active: Boolean, content: @Composable () -> Unit) {
    val tint by
        animateColorAsState(
            targetValue = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (active) HIGHLIGHT_ALPHA else 0f),
            animationSpec = tween(durationMillis = HIGHLIGHT_FADE_MILLIS),
            label = "fieldHighlight",
        )
    Box(modifier = Modifier.background(tint, RectangleShape)) { content() }
}

private const val HIGHLIGHT_ALPHA = 0.6f
private const val HIGHLIGHT_FADE_MILLIS = 300
internal const val HIGHLIGHT_HOLD_MILLIS = 1_800L
