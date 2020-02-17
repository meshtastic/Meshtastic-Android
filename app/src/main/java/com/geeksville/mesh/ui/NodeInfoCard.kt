package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.ui.core.Text
import androidx.ui.layout.*
import androidx.ui.material.EmphasisLevels
import androidx.ui.material.MaterialTheme
import androidx.ui.material.ProvideEmphasis
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import androidx.ui.core.Modifier as Modifier1


/*
@Composable
fun NodeIcon(modifier: Modifier1 = Modifier1.None, node: NodeInfo) {
    Column {
        Container(modifier = modifier + LayoutSize(40.dp, 40.dp)) {
            VectorImage(id = if (node.user?.shortName != null) R.drawable.person else R.drawable.help)
        }

        // Show our shortname if possible
        /* node.user?.shortName?.let {
            Text(it)
        } */

    }
}
 */

@Composable
fun CompassHeading(modifier: Modifier1 = Modifier1.None, node: NodeInfo) {
    Column {
        if (node.position != null) {
            Container(modifier = modifier + LayoutSize(40.dp, 40.dp)) {
                VectorImage(id = R.drawable.navigation)
            }
        } else Container(modifier = modifier + LayoutSize(40.dp, 40.dp)) {
            VectorImage(id = R.drawable.help)
        }
        Text("2.3 km") // always reserve space for the distance even if we aren't showing it
    }
}

@Composable
fun NodeHeading(node: NodeInfo) {
    ProvideEmphasis(emphasis = EmphasisLevels().high) {
        Text(
            node.user?.longName ?: "unknown",
            style = MaterialTheme.typography().subtitle1
            //modifier = LayoutWidth.Fill
        )
    }
}

/**
 * An info card for a node:
 *
 * on left, the icon for the user (or shortname if that is all we have) (this includes user's distance and heading arrow)
 *
 * Middle is users fullname
 *
 */
@Composable
fun NodeInfoCard(node: NodeInfo) {
    // Text("Node: ${it.user?.longName}")
    Row(modifier = LayoutPadding(16.dp)) {
        UserIcon(
            modifier = LayoutPadding(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp),
            user = node
        )

        NodeHeading(node)

        // FIXME - show compass instead
        // CompassHeading(node = node)
    }
}

@Preview
@Composable
fun nodeInfoPreview() {
    Column {
        NodeInfoCard(NodeDB.testNodes[0])
        NodeInfoCard(NodeDB.testNodeNoPosition)
    }
}