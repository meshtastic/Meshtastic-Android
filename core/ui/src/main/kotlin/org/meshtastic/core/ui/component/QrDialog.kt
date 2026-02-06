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
@file:Suppress("detekt:ALL")

package org.meshtastic.core.ui.component

import android.content.ClipData
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.copy
import org.meshtastic.core.strings.okay
import org.meshtastic.core.strings.qr_code
import org.meshtastic.core.strings.url
import org.meshtastic.core.ui.util.findActivity

private const val QR_IMAGE_SIZE = 320

@Composable
fun QrDialog(title: String, uri: Uri, qrCode: Bitmap?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val label = stringResource(Res.string.url)

    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val originalBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
        activity?.window?.let { window ->
            val params = window.attributes
            params.screenBrightness = 1f
            window.attributes = params
        }
        onDispose {
            activity?.window?.let { window ->
                val params = window.attributes
                params.screenBrightness = originalBrightness
                window.attributes = params
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(16.dp),
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (qrCode != null) {
                    Image(
                        painter = BitmapPainter(qrCode.asImageBitmap()),
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
                        text = uri.toString(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        overflow = TextOverflow.Visible,
                        softWrap = true,
                    )
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText(label, uri.toString())))
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.ContentCopy,
                            contentDescription = stringResource(Res.string.copy),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(text = stringResource(Res.string.okay)) } },
    )
}
