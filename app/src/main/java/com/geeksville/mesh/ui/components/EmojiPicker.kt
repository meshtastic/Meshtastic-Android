/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.RecentEmojiProviderAdapter
import com.geeksville.mesh.util.CustomRecentEmojiProvider

@Composable
fun EmojiPicker(
    onDismiss: () -> Unit = {},
    onConfirm: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.Bottom
    ) {
        BackHandler {
            onDismiss()
        }
        AndroidView(
            factory = { context ->
                androidx.emoji2.emojipicker.EmojiPickerView(context).apply {
                    clipToOutline = true
                    setRecentEmojiProvider(
                        RecentEmojiProviderAdapter(CustomRecentEmojiProvider(context))
                    )
                    setOnEmojiPickedListener { emoji ->
                        onDismiss()
                        onConfirm(emoji.emoji)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction = 0.4f)
                .background(MaterialTheme.colors.background)
        )
    }
}
