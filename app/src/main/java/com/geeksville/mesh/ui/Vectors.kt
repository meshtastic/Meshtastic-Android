package com.geeksville.mesh.ui

import androidx.annotation.DrawableRes
import androidx.compose.Composable
import androidx.ui.core.Modifier
import androidx.ui.core.WithDensity
import androidx.ui.foundation.Clickable
import androidx.ui.graphics.Color
import androidx.ui.graphics.vector.DrawVector
import androidx.ui.layout.Container
import androidx.ui.layout.LayoutSize
import androidx.ui.material.ripple.Ripple
import androidx.ui.res.vectorResource
import androidx.ui.unit.dp


@Composable
fun VectorImageButton(@DrawableRes id: Int, onClick: () -> Unit) {
    Ripple(bounded = false) {
        Clickable(onClick = onClick) {
            VectorImage(id = id, modifier = LayoutSize(40.dp, 40.dp))
        }
    }
}

/* fun AppBarIcon(icon: Image, onClick: () -> Unit) {
    Container(width = ActionIconDiameter, height = ActionIconDiameter) {
        Ripple(bounded = false) {
            Clickable(onClick = onClick) {
                SimpleImage(icon)
            }
        }
    }
} */

@Composable
fun VectorImage(
    modifier: Modifier = Modifier.None, @DrawableRes id: Int,
    tint: Color = Color.Transparent
) {
    val vector = vectorResource(id)
    WithDensity {
        Container(
            modifier = modifier + LayoutSize(
                vector.defaultWidth,
                vector.defaultHeight
            )
        ) {
            DrawVector(vector, tint)
        }
    }
}
