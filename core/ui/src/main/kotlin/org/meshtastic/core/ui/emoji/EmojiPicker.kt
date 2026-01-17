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
package org.meshtastic.core.ui.emoji

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.RecentEmojiProviderAdapter
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.meshtastic.core.ui.component.BottomSheetDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPicker(
    modifier: Modifier = Modifier,
    viewModel: EmojiPickerViewModel = hiltViewModel(),
    onDismiss: () -> Unit = {},
    onConfirm: (String) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.Bottom) {
        BackHandler { onDismiss() }
        AndroidView(
            factory = { context ->
                androidx.emoji2.emojipicker.EmojiPickerView(context).apply {
                    clipToOutline = true
                    isNestedScrollingEnabled = true
                    setRecentEmojiProvider(
                        RecentEmojiProviderAdapter(
                            CustomRecentEmojiProvider(viewModel.customEmojiFrequency) { updatedValue ->
                                viewModel.customEmojiFrequency = updatedValue
                            },
                        ),
                    )
                    setOnEmojiPickedListener { emoji ->
                        onDismiss()
                        onConfirm(emoji.emoji)
                    }
                }
            },
            update = { view -> view.isNestedScrollingEnabled = true },
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerDialog(onDismiss: () -> Unit = {}, onConfirm: (String) -> Unit) =
    BottomSheetDialog(onDismiss = onDismiss) { EmojiPicker(onConfirm = onConfirm, onDismiss = onDismiss) }
