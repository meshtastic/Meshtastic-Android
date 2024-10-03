package com.geeksville.mesh.ui.map

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.geeksville.mesh.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun MapButton(
    onClick: () -> Unit,
    @DrawableRes drawableRes: Int,
    @StringRes contentDescription: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    MapButton(
        onClick = onClick,
        drawableRes = drawableRes,
        contentDescription = stringResource(contentDescription),
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun MapButton(
    onClick: () -> Unit,
    @DrawableRes drawableRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp),
    ) {
        Icon(
            painterResource(id = drawableRes),
            contentDescription,
            modifier = Modifier.scale(scale = 1.5f),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MapButtonPreview() {
    MapButton(
        onClick = {},
        drawableRes = R.drawable.ic_twotone_layers_24,
        R.string.map_style_selection,
    )
}
