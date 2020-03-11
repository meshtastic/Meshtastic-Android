package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.ui.core.ContextAmbient
import androidx.ui.layout.Column
import androidx.ui.layout.LayoutPadding
import androidx.ui.layout.LayoutSize
import androidx.ui.material.MaterialTheme
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp


@Composable
fun MapContent() {
    analyticsScreen(name = "channel")

    val typography = MaterialTheme.typography()
    val context = ContextAmbient.current

    Column(modifier = LayoutSize.Fill + LayoutPadding(16.dp)) {
    
    }
}


@Preview
@Composable
fun previewMap() {
    // another bug? It seems modaldrawerlayout not yet supported in preview
    MaterialTheme(colors = palette) {
        MapContent()
    }
}
