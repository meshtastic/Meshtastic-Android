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

import android.content.ClipData
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.meshtastic.core.strings.R as Res

@Composable
fun CopyIconButton(
    valueToCopy: String,
    modifier: Modifier = Modifier,
    label: String = stringResource(Res.string.copy),
) {
    val clipboardManager = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    IconButton(
        modifier = modifier,
        onClick = {
            coroutineScope.launch {
                val clipData = ClipData.newPlainText(label, valueToCopy)
                val clipEntry = ClipEntry(clipData)
                clipboardManager.setClipEntry(clipEntry)
            }
        },
    ) {
        Icon(imageVector = Icons.TwoTone.ContentCopy, contentDescription = label)
    }
}
