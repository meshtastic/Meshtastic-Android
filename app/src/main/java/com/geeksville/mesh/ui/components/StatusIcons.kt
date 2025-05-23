package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.NoCell
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R

@Composable
fun StatusIcons(
    isFavorite: Boolean,
    unmessageable: Boolean
) {
Row(
    modifier = Modifier.padding(5.dp)
) {
    if (isFavorite) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = "Favorite",
            modifier = Modifier
                .size(25.dp), // Smaller size for badge
            tint = Color(color = 0xFFFEC30A)
        )
    }
    if (unmessageable) {
        Icon(
            imageVector = Icons.Outlined.NoCell,
            contentDescription = stringResource(R.string.unmessageable),
            modifier = Modifier
                .size(25.dp), // Smaller size for badge
            tint = MaterialTheme.colorScheme.error,
        )
    }
}
}

@Preview
@Composable
fun StatusIconsPreview() {
    StatusIcons(isFavorite = true, unmessageable = true)
}