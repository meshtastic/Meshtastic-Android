package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.ui.core.Modifier
import androidx.ui.core.Text
import androidx.ui.foundation.DrawImage
import androidx.ui.layout.Container
import androidx.ui.layout.LayoutPadding
import androidx.ui.layout.LayoutSize
import androidx.ui.layout.Row
import androidx.ui.material.EmphasisLevels
import androidx.ui.material.MaterialTheme
import androidx.ui.material.ProvideEmphasis
import androidx.ui.res.imageResource
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R


@Composable
fun NodeIcon(modifier: Modifier = Modifier.None, node: NodeInfo) {
    val image = imageResource(R.drawable.ic_launcher_foreground)

    Container(modifier = modifier + LayoutSize(40.dp, 40.dp)) {
        DrawImage(image)
    }
}

@Composable
fun NodeHeading(node: NodeInfo) {
    ProvideEmphasis(emphasis = EmphasisLevels().high) {
        Text(node.user?.longName ?: "unknown", style = MaterialTheme.typography().subtitle1)
    }
}

/**
 * An info card for a node:
 *
 * on left, the icon for the user (or shortname if that is all we have)
 *
 * Middle is users fullname
 *
 * on right a compass rose with a pointer to the user and distance
 *
 */
@Composable
fun NodeInfoCard(node: NodeInfo) {
    // Text("Node: ${it.user?.longName}")
    Row(modifier = LayoutPadding(16.dp)) {
        NodeIcon(
            modifier = LayoutPadding(left = 0.dp, top = 0.dp, right = 16.dp, bottom = 0.dp),
            node = node
        )

        NodeHeading(node)

        // FIXME - show compass instead
        NodeIcon(node = node)
    }
}

@Preview
@Composable
fun nodeInfoPreview() {
    NodeInfoCard(UIState.testNodes[0])
}