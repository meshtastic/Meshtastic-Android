package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.compose.Model
import androidx.ui.core.Text
import androidx.ui.layout.Column
import androidx.ui.layout.Row
import androidx.ui.tooling.preview.Preview

@Model
data class BTScanEntry(val name: String, val macAddress: String, var selected: Boolean)

@Composable
fun BTScanCard(node: BTScanEntry) {
    // Text("Node: ${it.user?.longName}")
    Row {
        Text(node.name)

        Text(node.selected.toString())
    }
}

@Preview
@Composable
fun btScanPreview() {
    Column {
        BTScanCard(BTScanEntry("Meshtastic_ab12", "xx", true))
        BTScanCard(BTScanEntry("Meshtastic_32ac", "xx", false))
    }
}