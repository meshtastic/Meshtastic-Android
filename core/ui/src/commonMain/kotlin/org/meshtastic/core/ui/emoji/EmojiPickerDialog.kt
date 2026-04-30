/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
@file:Suppress("TooManyFunctions")

package org.meshtastic.core.ui.emoji

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.clear
import org.meshtastic.core.resources.search_emoji
import org.meshtastic.core.ui.component.BottomSheetDialog
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Search

// ── Constants ──────────────────────────────────────────────────────────────────

private val GRID_MIN_CELL_SIZE = 44.dp
private const val EMOJI_FONT_SIZE = 24
private const val CATEGORY_HEADER_KEY_PREFIX = "header_"
private const val RECENTS_HEADER_KEY = "header_recents"
private const val RECENTS_KEY_PREFIX = "recent_"
private const val MAX_RECENTS = 30
private const val DEFAULT_QUICK_REACTION_COUNT = 6

/** Default quick-reaction emoji used when the user has no recents. */
private val DEFAULT_QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

// ── Public API ─────────────────────────────────────────────────────────────────

/**
 * A fully-featured, cross-platform emoji picker dialog.
 *
 * Features:
 * - **9 categories** with tab-strip navigation
 * - **Recents** — most-frequently-used emojis, persisted via [EmojiPickerViewModel]
 * - **Search** — filters the full catalog by keyword
 * - **Per-emoji skin-tone popup** — long-press on a skin-tone-capable emoji to choose a variant
 * - **Selected-emoji highlighting** — visually marks already-applied reactions
 * - **Responsive grid** — adapts column count to screen width (phones ≈ 8, desktop ≈ 12+)
 *
 * @param selectedEmojis Set of emoji strings already selected (e.g. applied reactions). Matched emojis are highlighted
 *   with a tinted background.
 */
@Composable
fun EmojiPickerDialog(
    onDismiss: () -> Unit = {},
    selectedEmojis: Set<String> = emptySet(),
    onConfirm: (String) -> Unit,
) {
    val viewModel: EmojiPickerViewModel = koinViewModel()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategoryIndex by rememberSaveable { mutableStateOf(0) }

    val recentEmojis by
        remember(viewModel.customEmojiFrequency) { derivedStateOf { parseRecents(viewModel.customEmojiFrequency) } }

    BottomSheetDialog(onDismiss = onDismiss, modifier = Modifier.fillMaxHeight(fraction = .55f)) {
        EmojiPickerContent(
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            selectedCategoryIndex = selectedCategoryIndex,
            onCategorySelected = { selectedCategoryIndex = it },
            selectedEmojis = selectedEmojis,
            recentEmojis = recentEmojis,
            onEmojiSelected = { emoji ->
                recordSelection(emoji, viewModel)
                onDismiss()
                onConfirm(emoji)
            },
        )
    }
}

/**
 * Returns the user's top quick-reaction emoji from recents, falling back to defaults.
 *
 * Call sites (e.g. message long-press menus) can use this to populate a dynamic quick-reaction row sourced from the
 * user's actual usage patterns.
 */
@Composable
fun rememberQuickReactions(count: Int = DEFAULT_QUICK_REACTION_COUNT): List<String> {
    val viewModel: EmojiPickerViewModel = koinViewModel()
    val recents by
        remember(viewModel.customEmojiFrequency) { derivedStateOf { parseRecents(viewModel.customEmojiFrequency) } }
    return remember(recents) {
        if (recents.size >= count) {
            recents.take(count)
        } else {
            // Pad with defaults that aren't already in recents
            val padded = recents.toMutableList()
            for (default in DEFAULT_QUICK_REACTIONS) {
                if (padded.size >= count) break
                if (default !in padded) padded.add(default)
            }
            padded.take(count)
        }
    }
}

// ── Main Content ───────────────────────────────────────────────────────────────

@Composable
@Suppress("LongParameterList")
private fun EmojiPickerContent(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCategoryIndex: Int,
    onCategorySelected: (Int) -> Unit,
    selectedEmojis: Set<String>,
    recentEmojis: List<String>,
    onEmojiSelected: (String) -> Unit,
) {
    Column {
        SearchBar(query = searchQuery, onQueryChange = onSearchQueryChange)

        AnimatedVisibility(visible = searchQuery.isBlank(), enter = fadeIn(), exit = fadeOut()) {
            CategoryTabStrip(
                selectedIndex = selectedCategoryIndex,
                onCategorySelected = onCategorySelected,
                hasRecents = recentEmojis.isNotEmpty(),
            )
        }

        EmojiGrid(
            searchQuery = searchQuery,
            selectedCategoryIndex = selectedCategoryIndex,
            onCategoryChanged = onCategorySelected,
            selectedEmojis = selectedEmojis,
            recentEmojis = recentEmojis,
            onEmojiSelected = onEmojiSelected,
        )
    }
}

// ── Search Bar ─────────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        placeholder = {
            Text(
                text = stringResource(Res.string.search_emoji),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(imageVector = MeshtasticIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = MeshtasticIcons.Close,
                        contentDescription = stringResource(Res.string.clear),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors =
        TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

// ── Category Tabs ──────────────────────────────────────────────────────────────

@Composable
private fun CategoryTabStrip(selectedIndex: Int, onCategorySelected: (Int) -> Unit, hasRecents: Boolean) {
    val tabOffset = if (hasRecents) 1 else 0
    val totalTabs = EmojiData.categories.size + tabOffset

    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = Modifier.fillMaxWidth(),
        edgePadding = 4.dp,
        divider = {},
        containerColor = Color.Transparent,
    ) {
        repeat(totalTabs) { index ->
            val isRecents = hasRecents && index == 0
            Tab(
                selected = selectedIndex == index,
                onClick = { onCategorySelected(index) },
                text = {
                    Text(
                        text = if (isRecents) "\uD83D\uDD50" else EmojiData.categories[index - tabOffset].icon,
                        fontSize = 18.sp,
                    )
                },
            )
        }
    }
}

// ── Emoji Grid ─────────────────────────────────────────────────────────────────

@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
private fun EmojiGrid(
    searchQuery: String,
    selectedCategoryIndex: Int,
    onCategoryChanged: (Int) -> Unit,
    selectedEmojis: Set<String>,
    recentEmojis: List<String>,
    onEmojiSelected: (String) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val hasRecents = recentEmojis.isNotEmpty()
    val tabOffset = if (hasRecents) 1 else 0

    val gridItems: List<GridItem> = remember(searchQuery, recentEmojis) { buildGridItems(searchQuery, recentEmojis) }
    var animationTargetIndex by remember { mutableStateOf<Int?>(null) }

    // Scroll to category when tab changes
    LaunchedEffect(selectedCategoryIndex) {
        if (searchQuery.isNotBlank()) return@LaunchedEffect
        if (selectedCategoryIndex == animationTargetIndex) return@LaunchedEffect

        val targetKey =
            if (hasRecents && selectedCategoryIndex == 0) {
                RECENTS_HEADER_KEY
            } else {
                val catIndex = selectedCategoryIndex - tabOffset
                if (catIndex in EmojiData.categories.indices) {
                    CATEGORY_HEADER_KEY_PREFIX + catIndex
                } else {
                    null
                }
            }
        targetKey?.let { key ->
            val itemIndex = gridItems.indexOfFirst { it is GridItem.Header && it.key == key }
            if (itemIndex >= 0) {
                try {
                    animationTargetIndex = selectedCategoryIndex
                    gridState.animateScrollToItem(itemIndex)
                } finally {
                    animationTargetIndex = null
                }
            }
        }
    }

    // Sync tab selection with scroll position
    LaunchedEffect(gridState, searchQuery) {
        if (searchQuery.isNotBlank()) return@LaunchedEffect
        snapshotFlow { gridState.firstVisibleItemIndex }
            .collect { firstVisible ->
                if (animationTargetIndex != null) return@collect
                for (i in firstVisible downTo 0) {
                    val item = gridItems.getOrNull(i)
                    if (item is GridItem.Header) {
                        val newIndex =
                            if (item.key == RECENTS_HEADER_KEY) {
                                0
                            } else {
                                val catIdx = item.key.removePrefix(CATEGORY_HEADER_KEY_PREFIX).toIntOrNull()
                                if (catIdx != null) catIdx + tabOffset else selectedCategoryIndex
                            }
                        if (newIndex != selectedCategoryIndex) {
                            onCategoryChanged(newIndex)
                        }
                        break
                    }
                }
            }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = GRID_MIN_CELL_SIZE),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        gridItems.forEach { item ->
            when (item) {
                is GridItem.Header ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = item.key) { SectionHeader(title = item.title) }
                is GridItem.EmojiCell ->
                    item(key = item.key) {
                        EmojiCellWithSkinTone(
                            emoji = item.emoji,
                            isSelected = selectedEmojis.contains(item.emoji.base),
                            onSelect = onEmojiSelected,
                        )
                    }
            }
        }

        if (gridItems.none { it is GridItem.EmojiCell }) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "No emoji found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Grid Item Model ────────────────────────────────────────────────────────────

private sealed class GridItem(open val key: String) {
    data class Header(val title: String, override val key: String) : GridItem(key)

    data class EmojiCell(val emoji: Emoji, override val key: String) : GridItem(key)
}

@Suppress("CyclomaticComplexMethod")
private fun buildGridItems(searchQuery: String, recentEmojis: List<String>): List<GridItem> = buildList {
    if (searchQuery.isNotBlank()) {
        val query = searchQuery.lowercase()
        val results =
            EmojiData.all.filter { emoji -> emoji.keywords.any { it.contains(query) } || emoji.base.contains(query) }
        results.forEachIndexed { i, emoji -> add(GridItem.EmojiCell(emoji, "search_$i")) }
    } else {
        if (recentEmojis.isNotEmpty()) {
            add(GridItem.Header("Recently Used", RECENTS_HEADER_KEY))
            recentEmojis.forEachIndexed { i, emojiStr ->
                add(GridItem.EmojiCell(Emoji(emojiStr), "$RECENTS_KEY_PREFIX$i"))
            }
        }
        EmojiData.categories.forEachIndexed { catIndex, category ->
            add(GridItem.Header(category.name, "$CATEGORY_HEADER_KEY_PREFIX$catIndex"))
            category.emojis.forEachIndexed { emojiIndex, emoji ->
                add(GridItem.EmojiCell(emoji, "cat_${catIndex}_$emojiIndex"))
            }
        }
    }
}

// ── Cell Components ────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/**
 * An emoji grid cell that supports:
 * - **Tap** → select the emoji (with default skin tone)
 * - **Long-press** → if the emoji supports skin tones, show a popup with 6 Fitzpatrick variants
 * - **Selected highlight** → tinted background when the emoji is in [isSelected]
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiCellWithSkinTone(emoji: Emoji, isSelected: Boolean, onSelect: (String) -> Unit) {
    var showSkinTonePopup by rememberSaveable { mutableStateOf(false) }

    Box {
        Box(
            modifier =
            Modifier.size(GRID_MIN_CELL_SIZE)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (isSelected) {
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                    } else {
                        Modifier
                    },
                )
                .combinedClickable(
                    onClick = { onSelect(emoji.base) },
                    onLongClick =
                    if (emoji.supportsSkinTone) {
                        { showSkinTonePopup = true }
                    } else {
                        null
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji.base, fontSize = EMOJI_FONT_SIZE.sp, textAlign = TextAlign.Center)
            // Small dot indicator for skin-tone-capable emoji
            if (emoji.supportsSkinTone) {
                Box(
                    modifier =
                    Modifier.align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), CircleShape),
                )
            }
        }

        if (showSkinTonePopup) {
            SkinTonePopup(
                emoji = emoji,
                onSelect = { variant ->
                    showSkinTonePopup = false
                    onSelect(variant)
                },
                onDismiss = { showSkinTonePopup = false },
            )
        }
    }
}

// ── Skin Tone Popup ────────────────────────────────────────────────────────────

@Composable
private fun SkinTonePopup(emoji: Emoji, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    Popup(alignment = Alignment.TopCenter, onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Row(modifier = Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                SkinTone.entries.forEach { tone ->
                    val variant = emoji.withSkinTone(tone)
                    Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).clickable { onSelect(variant) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = variant, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

// ── Frequency Tracking ─────────────────────────────────────────────────────────

private const val SPLIT_CHAR = ","
private const val KEY_VALUE_DELIMITER = "="

internal fun parseRecents(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(SPLIT_CHAR)
        .mapNotNull { entry ->
            entry
                .split(KEY_VALUE_DELIMITER, limit = 2)
                .takeIf { it.size == 2 }
                ?.let { it[0] to (it[1].toIntOrNull() ?: 0) }
        }
        .sortedByDescending { it.second }
        .take(MAX_RECENTS)
        .map { it.first }
}

private fun recordSelection(emoji: String, viewModel: EmojiPickerViewModel) {
    val raw = viewModel.customEmojiFrequency
    val freq =
        if (raw.isNullOrBlank()) {
            mutableMapOf()
        } else {
            raw.split(SPLIT_CHAR)
                .mapNotNull { entry ->
                    entry
                        .split(KEY_VALUE_DELIMITER, limit = 2)
                        .takeIf { it.size == 2 }
                        ?.let { it[0] to (it[1].toIntOrNull() ?: 0) }
                }
                .toMap()
                .toMutableMap()
        }
    freq[emoji] = (freq[emoji] ?: 0) + 1
    viewModel.customEmojiFrequency =
        freq.entries.joinToString(SPLIT_CHAR) { "${it.key}$KEY_VALUE_DELIMITER${it.value}" }
}
