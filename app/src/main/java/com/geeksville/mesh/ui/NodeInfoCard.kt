package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.ui.core.Modifier
import androidx.ui.core.Text
import androidx.ui.layout.*
import androidx.ui.material.EmphasisLevels
import androidx.ui.material.MaterialTheme
import androidx.ui.material.ProvideEmphasis
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R


@Composable
fun NodeIcon(modifier: Modifier = Modifier.None, node: NodeInfo) {
    Column {
        Container(modifier = modifier + LayoutSize(40.dp, 40.dp)) {
            VectorImage(id = if (node.user?.shortName != null) R.drawable.person else R.drawable.help)
        }

        // Show our shortname if possible
        node.user?.shortName?.let {
            Text(it)
        }

    }
}

@Composable
fun CompassHeading(modifier: Modifier = Modifier.None, node: NodeInfo) {
    Column {
        Container(modifier = modifier + LayoutSize(40.dp, 40.dp)) {
            VectorImage(id = R.drawable.navigation)
        }
        Text("2.3 km")
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
            modifier = LayoutPadding(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp),
            node = node
        )

        NodeHeading(node)

        // FIXME - show compass instead
        CompassHeading(node = node)
    }
}

@Preview
@Composable
fun nodeInfoPreview() {
    NodeInfoCard(UIState.testNodes[0])
}