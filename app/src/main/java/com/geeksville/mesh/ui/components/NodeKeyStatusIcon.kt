package com.geeksville.mesh.ui.components

import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
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
    onClick: () -> Unit = {},
) = IconButton(
    onClick = onClick,
    modifier = modifier,
) {
    val (icon, tint) = when {
        mismatchKey -> rememberVectorPainter(Icons.Default.KeyOff) to Color.Red
        hasPKC -> rememberVectorPainter(Icons.Default.Lock) to Color(color = 0xFF30C047)
        else -> painterResource(R.drawable.ic_lock_open_right_24) to Color(color = 0xFFFEC30A)
    }
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
