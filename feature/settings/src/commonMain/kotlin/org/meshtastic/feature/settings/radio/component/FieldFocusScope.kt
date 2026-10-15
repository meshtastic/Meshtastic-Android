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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import org.jetbrains.compose.resources.StringResource

/**
 * Marks the row that edits [fields], so a settings search result naming one of them scrolls this row into view and
 * tints it. Outside a config screen's list host it does nothing.
 */
internal fun Modifier.settingsField(vararg fields: StringResource): Modifier =
    this then SettingsFieldElement(fields.mapTo(HashSet()) { it.key })

/** [settingsField] by schema key, for rows whose key is not a single string resource at hand. */
internal fun Modifier.settingsField(vararg keys: String): Modifier = this then SettingsFieldElement(keys.toSet())

/**
 * The field a search result named on one config screen, the tint its row draws over itself, and the rows that declared
 * themselves with [settingsField].
 */
@Stable
internal class FieldHighlight(private val tint: State<Color>) {
    var key: String? by mutableStateOf(null)

    private val anchors = mutableStateMapOf<String, FieldAnchor>()

    /** Bumped when a row is first placed, so a host waiting to center a row can re-measure. */
    var placements by mutableIntStateOf(0)
        private set

    /** The tint for a row carrying [keys]: transparent unless one of them is the target. */
    fun tintFor(keys: Collection<String>): Color = key?.takeIf { it in keys }?.let { tint.value } ?: Color.Transparent

    /** The tint for a card standing for [keys], drawn only when no row on it claims the target itself. */
    fun fallbackTintFor(keys: Collection<String>): Color =
        key?.takeIf { it in keys && it !in anchors }?.let { tint.value } ?: Color.Transparent

    /** The list's own layout, set by the host so a row can be measured against the viewport. */
    var listCoordinates: LayoutCoordinates? by mutableStateOf(null)

    /**
     * How far the list must scroll for the row carrying [fieldKey] to sit at the viewport's center; null until that row
     * and the list have both been placed.
     */
    fun offsetToCenter(fieldKey: String): Float? {
        val list = listCoordinates?.takeIf { it.isAttached }
        val row = anchors[fieldKey]?.placedCoordinates?.takeIf { it.isAttached }
        if (list == null || row == null) return null
        val rowCenter = list.localPositionOf(row, Offset.Zero).y + row.size.height / 2f
        return rowCenter - list.size.height / 2f
    }

    fun register(keys: Collection<String>, anchor: FieldAnchor) {
        keys.forEach { anchors[it] = anchor }
    }

    fun unregister(keys: Collection<String>, anchor: FieldAnchor) {
        keys.forEach { if (anchors[it] === anchor) anchors.remove(it) }
    }

    fun onAnchorPlaced() {
        placements++
    }
}

/** A row marked with [settingsField]: its coordinates once placed, null before. */
internal interface FieldAnchor {
    val placedCoordinates: LayoutCoordinates?
}

@Suppress("CompositionLocalAllowlist")
internal val LocalFieldHighlight = compositionLocalOf<FieldHighlight?> { null }

private data class SettingsFieldElement(val keys: Set<String>) : ModifierNodeElement<SettingsFieldNode>() {
    override fun create() = SettingsFieldNode(keys)

    override fun update(node: SettingsFieldNode) = node.update(keys)

    override fun InspectorInfo.inspectableProperties() {
        name = "settingsField"
        properties["keys"] = keys
    }
}

private class SettingsFieldNode(private var keys: Set<String>) :
    Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    DrawModifierNode,
    LayoutAwareModifierNode,
    FieldAnchor {

    private var highlight: FieldHighlight? = null

    override var placedCoordinates: LayoutCoordinates? = null
        private set

    override fun onAttach() {
        highlight = currentValueOf(LocalFieldHighlight)?.also { it.register(keys, this) }
    }

    override fun onDetach() {
        highlight?.unregister(keys, this)
        highlight = null
        placedCoordinates = null
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        if (coordinates === placedCoordinates) return
        placedCoordinates = coordinates
        highlight?.onAnchorPlaced()
    }

    fun update(newKeys: Set<String>) {
        if (newKeys == keys) return
        highlight?.unregister(keys, this)
        keys = newKeys
        highlight?.register(keys, this)
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val tint = highlight?.tintFor(keys) ?: return
        if (tint.alpha > 0f) drawRect(tint)
    }
}

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
 * Forwards to [delegate] while noting each item's key in [index]. An item keyed by the target field is tinted whole; a
 * card standing for several fields is tinted only when none of its rows marks the target with [settingsField].
 */
internal class FieldKeyRecordingScope(
    private val delegate: LazyListScope,
    private val index: FieldKeyIndex,
    private val highlight: FieldHighlight,
) : LazyListScope by delegate {

    /** A card standing for [fieldKeys]: search scrolls to it so its rows compose, then lands on the row. */
    fun fieldItem(fieldKeys: Collection<String>, content: @Composable LazyItemScope.() -> Unit) {
        index.recordFields(fieldKeys)
        val highlight = highlight
        delegate.item(key = fieldKeys.firstOrNull()) {
            Tinted({ highlight.fallbackTintFor(fieldKeys) }) { content() }
        }
    }

    override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
        index.recordItem(key)
        val highlight = highlight
        if (key is String) {
            delegate.item(key, contentType) { Tinted({ highlight.tintFor(listOf(key)) }) { content() } }
        } else {
            delegate.item(key, contentType, content)
        }
    }

    override fun items(
        count: Int,
        key: ((Int) -> Any)?,
        contentType: (Int) -> Any?,
        itemContent: @Composable LazyItemScope.(Int) -> Unit,
    ) {
        index.recordItems(count, key)
        val highlight = highlight
        delegate.items(count, key, contentType) { i ->
            val itemKey = key?.invoke(i)
            if (itemKey is String) {
                Tinted({ highlight.tintFor(listOf(itemKey)) }) { itemContent(i) }
            } else {
                itemContent(i)
            }
        }
    }
}

/**
 * An item that holds the controls [fieldKeys] name. Config screens use it so a search result can scroll to the card
 * carrying its control; outside the list host it is a plain item.
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

/** Draws [tint] over [content], so an opaque card or row cannot hide it. */
@Composable
private fun Tinted(tint: () -> Color, content: @Composable () -> Unit) {
    Box(
        modifier =
        Modifier.drawWithContent {
            drawContent()
            val color = tint()
            if (color.alpha > 0f) drawRect(color)
        },
    ) {
        content()
    }
}

internal const val HIGHLIGHT_ALPHA = 0.24f
internal const val HIGHLIGHT_FADE_MILLIS = 300
internal const val HIGHLIGHT_HOLD_MILLIS = 1_800L

/** How long the host waits for the named row to be placed before it skips centering. */
internal const val PLACEMENT_WAIT_MILLIS = 1_000L
