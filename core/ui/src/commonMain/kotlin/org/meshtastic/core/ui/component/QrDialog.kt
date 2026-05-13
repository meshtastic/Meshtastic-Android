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
@file:Suppress("detekt:ALL")

package org.meshtastic.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.copy
import org.meshtastic.core.resources.okay
import org.meshtastic.core.resources.qr_code
import org.meshtastic.core.resources.url
import org.meshtastic.core.ui.icon.Copy
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.SetScreenBrightness
import org.meshtastic.core.ui.util.createClipEntry

private const val QR_IMAGE_SIZE = 320

@Composable
fun QrDialog(title: String, uriString: String, qrPainter: Painter?, onDismiss: () -> Unit) {
    val clipboardManager = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val label = stringResource(Res.string.url)

    SetScreenBrightness(1f)

    MeshtasticDialog(
        onDismiss = onDismiss,
        title = title,
        confirmText = stringResource(Res.string.okay),
        onConfirm = onDismiss,
        text = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (qrPainter != null) {
                    Image(
                        painter = qrPainter,
                        contentDescription = stringResource(Res.string.qr_code),
                        modifier = Modifier.size(QR_IMAGE_SIZE.dp),
                        contentScale = ContentScale.Fit,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = uriString,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        overflow = TextOverflow.Visible,
                        softWrap = true,
                    )
                    IconButton(
                        onClick = {
                            coroutineScope.launch { clipboardManager.setClipEntry(createClipEntry(uriString)) }
                        },
                    ) {
                        Icon(imageVector = MeshtasticIcons.Copy, contentDescription = stringResource(Res.string.copy))
                    }
                }
            }
        },
    )
}
