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

    /** Records one item that several fields share, such as a card holding a screen's rows. */
    fun recordFields(keys: Collection<String>) {
        keys.forEach { indices.getOrPut(it) { count } }
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

    /** A card standing for [fieldKeys]: search scrolls to it and tints it for any of them. */
    fun fieldItem(fieldKeys: Collection<String>, content: @Composable LazyItemScope.() -> Unit) {
        index.recordFields(fieldKeys)
        val highlighted = highlightKey != null && highlightKey in fieldKeys
        val active = highlightActive
        delegate.item(key = fieldKeys.firstOrNull()) {
            if (highlighted) {
                Highlighted(active) { content() }
            } else {
                content()
            }
        }
    }

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        index.recordItem(key)
        val highlighted = key != null && key == highlightKey
        val active = highlightActive
        delegate.item(key, contentType) {
            if (highlighted) {
                Highlighted(active) { content() }
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
        val target = highlightKey
        val active = highlightActive
        delegate.items(count, key, contentType) { i ->
            if (key != null && key(i) == target) {
                Highlighted(active) { itemContent(i) }
            } else {
                itemContent(i)
            }
        }
    }
}

/**
 * An item that holds the controls [fieldKeys] name. Config screens use it so a search result can scroll to, and tint,
 * the card carrying its control; outside the list host it is a plain item.
 */
internal fun LazyListScope.fieldItem(fieldKeys: Collection<String>, content: @Composable LazyItemScope.() -> Unit) {
    if (this is FieldKeyRecordingScope) {
        fieldItem(fieldKeys, content)
    } else {
        item(content = content)
    }
}

/** Splits [keys] into those [first] claims and the rest, for a screen whose controls span two cards. */
internal fun splitFieldKeys(keys: Set<String>, first: (String) -> Boolean): Pair<Set<String>, Set<String>> =
    keys.filter(first).toSet().let { it to (keys - it) }

/**
 * Splits [keys] across a screen's cards. Each of [claims] names the fields, without [prefix], that one later card
 * holds; the first group is every key no claim names, so a field the schema gains lands on the first card.
 */
internal fun partitionFieldKeys(keys: Set<String>, prefix: String, vararg claims: List<String>): List<Set<String>> {
    val claimed = claims.map { names -> names.map { prefix + it }.filter { it in keys }.toSet() }
    return listOf(keys - claimed.flatten().toSet()) + claimed
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
