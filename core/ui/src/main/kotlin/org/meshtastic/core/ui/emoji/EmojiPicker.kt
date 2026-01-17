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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.RecentEmojiProviderAdapter
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.meshtastic.core.ui.component.BottomSheetDialog

@Composable
fun EmojiPicker(
    viewModel: EmojiPickerViewModel = hiltViewModel(),
    onDismiss: () -> Unit = {},
    onConfirm: (String) -> Unit,
) {
    BackHandler { onDismiss() }
    AndroidView(
        factory = { context ->
            androidx.emoji2.emojipicker.EmojiPickerView(context).apply {
                clipToOutline = true
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
        modifier = Modifier.fillMaxWidth().wrapContentHeight().verticalScroll(rememberScrollState()),
    )
}

@Composable
fun EmojiPickerDialog(onDismiss: () -> Unit = {}, onConfirm: (String) -> Unit) =
    BottomSheetDialog(onDismiss = onDismiss, modifier = Modifier.fillMaxHeight(fraction = .4f)) {
        EmojiPicker(onConfirm = onConfirm, onDismiss = onDismiss)
    }
