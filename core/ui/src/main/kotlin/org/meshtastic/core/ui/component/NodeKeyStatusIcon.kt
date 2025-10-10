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

import android.util.Base64
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.protobuf.ByteString
import org.meshtastic.core.model.Channel
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.StatusColors.StatusGreen
import org.meshtastic.core.ui.theme.StatusColors.StatusRed
import org.meshtastic.core.ui.theme.StatusColors.StatusYellow

@Composable
private fun KeyStatusDialog(@StringRes title: Int, @StringRes text: Int, key: ByteString?, onDismiss: () -> Unit = {}) =
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Text(text = stringResource(id = title), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(text = stringResource(id = text), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    if (key != null && title == R.string.encryption_pkc) {
                        val keyString = Base64.encodeToString(key.toByteArray(), Base64.NO_WRAP)
                        Text(
                            text = stringResource(id = R.string.config_security_public_key) + ":",
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer { Text(text = keyString, textAlign = TextAlign.Center) }
                        Spacer(Modifier.height(8.dp))
                        CopyIconButton(valueToCopy = keyString, modifier = Modifier.padding(start = 8.dp))
                        Spacer(Modifier.height(16.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Text(text = stringResource(id = R.string.close))
                        }
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
        val (title, text) =
            when {
                mismatchKey -> R.string.encryption_error to R.string.encryption_error_text
                hasPKC -> R.string.encryption_pkc to R.string.encryption_pkc_text
                else -> R.string.encryption_psk to R.string.encryption_psk_text
            }
        KeyStatusDialog(title, text, publicKey) { showEncryptionDialog = false }
    }

    val (icon, tint) =
        when {
            mismatchKey -> Icons.Default.KeyOff to colorScheme.StatusRed
            hasPKC -> Icons.Default.Lock to colorScheme.StatusGreen
            else ->
                ImageVector.vectorResource(org.meshtastic.core.ui.R.drawable.ic_lock_open_right_24) to
                    colorScheme.StatusYellow
        }

    IconButton(onClick = { showEncryptionDialog = true }, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription =
            stringResource(
                id =
                when {
                    mismatchKey -> R.string.encryption_error
                    hasPKC -> R.string.encryption_pkc
                    else -> R.string.encryption_psk
                },
            ),
            tint = tint,
        )
    }
}

@PreviewLightDark
@Composable
private fun KeyStatusDialogErrorPreview() {
    AppTheme { KeyStatusDialog(title = R.string.encryption_error, text = R.string.encryption_error_text, key = null) }
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
    AppTheme { KeyStatusDialog(title = R.string.encryption_psk, text = R.string.encryption_psk_text, key = null) }
}
