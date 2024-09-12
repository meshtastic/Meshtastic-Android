package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.ChipDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NoEncryption
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.geeksville.mesh.R

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PkiLockChipLeadingIcon(pkiEncrypted: Boolean) {
    Icon(
        modifier = Modifier.size(ChipDefaults.LeadingIconSize),
        imageVector = if (pkiEncrypted) {
            Icons.Default.Lock
        } else {
            Icons.Default.NoEncryption
        },
        tint = if (pkiEncrypted) {
            Color.Green
        } else {
            Color.Red
        },
        contentDescription = if (pkiEncrypted) {
            stringResource(id = R.string.pki_encrypted)
        } else {
            stringResource(id = R.string.pki_not_encrypted)
        }
    )
}
