package com.geeksville.mesh.ui.map.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.geeksville.mesh.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun MapStyleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .padding(16.dp)
            .size(48.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = MaterialTheme.colors.primary
        ),
    ) {
        Icon(
            painterResource(id = R.drawable.ic_twotone_layers_24),
            stringResource(R.string.map_style_selection),
            modifier = Modifier.scale(1.5f)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MapStyleButtonPreview() {
    MapStyleButton(onClick = {})
}
