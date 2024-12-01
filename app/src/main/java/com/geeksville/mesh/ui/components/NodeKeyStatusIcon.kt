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

import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.geeksville.mesh.R

@Composable
fun NodeKeyStatusIcon(
    hasPKC: Boolean,
    mismatchKey: Boolean,
    modifier: Modifier = Modifier,
) {
    var showEncryptionDialog by remember { mutableStateOf(false) }
    if (showEncryptionDialog) {
        val (title, text) = when {
            mismatchKey -> R.string.encryption_error to R.string.encryption_error_text
            hasPKC -> R.string.encryption_pkc to R.string.encryption_pkc_text
            else -> R.string.encryption_psk to R.string.encryption_psk_text
        }
        SimpleAlertDialog(title, text) { showEncryptionDialog = false }
    }

    val (icon, tint) = when {
        mismatchKey -> rememberVectorPainter(Icons.Default.KeyOff) to Color.Red
        hasPKC -> rememberVectorPainter(Icons.Default.Lock) to Color(color = 0xFF30C047)
        else -> painterResource(R.drawable.ic_lock_open_right_24) to Color(color = 0xFFFEC30A)
    }

    IconButton(
        onClick = { showEncryptionDialog = true },
        modifier = modifier,
    ) {
        Icon(
            painter = icon,
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
