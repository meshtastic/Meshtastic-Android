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

import android.util.Base64
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.geeksville.mesh.R
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.ui.theme.AppTheme
import com.google.protobuf.ByteString

@Composable
private fun KeyStatusDialog(
    @StringRes title: Int,
    @StringRes text: Int,
    key: ByteString?,
    onDismiss: () -> Unit = {}
) = Dialog(
    onDismissRequest = onDismiss,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colors.background
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = stringResource(id = title),
                    color = MaterialTheme.colors.onBackground.copy(alpha = ContentAlpha.high),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(id = text),
                    color = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                if (key != null && title == R.string.encryption_pkc) {
                    val keyString = Base64.encodeToString(key.toByteArray(), Base64.NO_WRAP)
                    SelectionContainer {
                        Text(
                            text = "Public Key: $keyString",
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colors.onSurface,
                        ),
                    ) { Text(text = stringResource(id = R.string.close)) }
                }
            }
        }
    }
}

@Composable
fun NodeKeyStatusIcon(
    hasPKC: Boolean,
    mismatchKey: Boolean,
    publicKey: ByteString? = null,
    modifier: Modifier = Modifier,
) {
    var showEncryptionDialog by remember { mutableStateOf(false) }
    if (showEncryptionDialog) {
        val (title, text) = when {
            mismatchKey -> R.string.encryption_error to R.string.encryption_error_text
            hasPKC -> R.string.encryption_pkc to R.string.encryption_pkc_text
            else -> R.string.encryption_psk to R.string.encryption_psk_text
        }
        KeyStatusDialog(title, text, publicKey) { showEncryptionDialog = false }
    }

    val (icon, tint) = when {
        mismatchKey -> Icons.Default.KeyOff to Color.Red
        hasPKC -> Icons.Default.Lock to Color(color = 0xFF30C047)
        else -> ImageVector.vectorResource(R.drawable.ic_lock_open_right_24) to Color(color = 0xFFFEC30A)
    }

    IconButton(
        onClick = { showEncryptionDialog = true },
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(
                id = when {
                    mismatchKey -> R.string.encryption_error
                    hasPKC -> R.string.encryption_pkc
                    else -> R.string.encryption_psk
                }
            ),
            tint = tint,
        )
    }
}

@PreviewLightDark
@Composable
private fun KeyStatusDialogErrorPreview() {
    AppTheme {
        KeyStatusDialog(
            title = R.string.encryption_error,
            text = R.string.encryption_error_text,
            key = null,
        )
    }
}

@PreviewLightDark
@Composable
private fun KeyStatusDialogPkcPreview() {
    AppTheme {
        KeyStatusDialog(
            title = R.string.encryption_pkc,
            text = R.string.encryption_pkc_text,
            key = Channel.getRandomKey(),
        )
    }
}

@PreviewLightDark
@Composable
private fun KeyStatusDialogPskPreview() {
    AppTheme {
        KeyStatusDialog(
            title = R.string.encryption_psk,
            text = R.string.encryption_psk_text,
            key = null,
        )
    }
}
