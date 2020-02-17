package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.ui.core.Modifier
import androidx.ui.core.Text
import androidx.ui.layout.Column
import androidx.ui.layout.LayoutGravity
import androidx.ui.material.MaterialTheme
import androidx.ui.tooling.preview.Preview
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.model.NodeDB

/**
 * Show the user icon for a particular user with distance from the operator and a small pointer
 * indicating their direction
 */
@Composable
fun UserIcon(user: NodeInfo? = null, modifier: Modifier = Modifier.None) {
    Column(modifier = modifier) {
        VectorImage(
            id = R.drawable.ic_twotone_person_24,
            tint = palette.onSecondary,
            modifier = LayoutGravity.Center
        )
        val ourNodeInfo = NodeDB.ourNodeInfo
        val distance = ourNodeInfo.distanceStr(user)
        if (distance != null)
            Text(distance, modifier = LayoutGravity.Center)
    }
}

@Preview
@Composable
fun previewUserIcon() {
    // another bug? It seems modaldrawerlayout not yet supported in preview
    MaterialTheme(colors = palette) {
        UserIcon(NodeDB.testNodes[1])
    }
}
