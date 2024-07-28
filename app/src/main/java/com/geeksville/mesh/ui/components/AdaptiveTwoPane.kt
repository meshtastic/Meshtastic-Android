package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AdaptiveTwoPane(
    first: @Composable ColumnScope.() -> Unit,
    second: @Composable ColumnScope.() -> Unit,
) = BoxWithConstraints {
    val compactWidth = maxWidth < 600.dp
    Row {
        Column(modifier = Modifier.weight(1f)) {
            first()

            if (compactWidth) {
                second()
            }
        }

        if (!compactWidth) {
            Column(modifier = Modifier.weight(1f)) {
                second()
            }
        }
    }
}
