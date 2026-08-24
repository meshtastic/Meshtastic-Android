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
package org.meshtastic.core.ui.component

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.copy
import org.meshtastic.core.ui.icon.Copy
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.createClipEntry

/**
 * Copy-to-clipboard icon button.
 *
 * Set [sensitive] when [valueToCopy] is key material, so the clip is marked and the OS does not surface the value in
 * its paste preview. See [createClipEntry].
 */
@Composable
fun CopyIconButton(
    valueToCopy: String,
    modifier: Modifier = Modifier,
    label: String = stringResource(Res.string.copy),
    sensitive: Boolean = false,
) {
    val clipboardManager = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    IconButton(
        modifier = modifier,
        onClick = {
            coroutineScope.launch {
                val clipEntry = createClipEntry(valueToCopy, sensitive = sensitive)
                clipboardManager.setClipEntry(clipEntry)
            }
        },
    ) {
        Icon(imageVector = MeshtasticIcons.Copy, contentDescription = label)
    }
}
