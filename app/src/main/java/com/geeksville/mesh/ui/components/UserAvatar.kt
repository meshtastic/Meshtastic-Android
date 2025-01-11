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

package com.geeksville.mesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.ui.preview.NodePreviewParameterProvider
import com.geeksville.mesh.ui.theme.AppTheme

@Composable
fun UserAvatar(
    node: Node,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.button.copy(
        fontWeight = FontWeight.Normal,
    )
    val textLayoutResult = remember {
        textMeasurer.measure(text = "MMMM", style = textStyle)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(with(LocalDensity.current) { textLayoutResult.size.width.toDp() })
            .background(
                color = Color(node.colors.second),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    ) {
        Text(
            text = node.user.shortName.ifEmpty { "?" },
            color = Color(node.colors.first),
            style = textStyle,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AvatarPreview(
    @PreviewParameter(NodePreviewParameterProvider::class)
    node: Node
) {
    AppTheme {
        UserAvatar(node)
    }
}
