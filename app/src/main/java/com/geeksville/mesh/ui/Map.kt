package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.ui.androidview.AndroidView
import androidx.ui.core.ContextAmbient
import androidx.ui.material.MaterialTheme
import androidx.ui.tooling.preview.Preview
import com.geeksville.mesh.R


@Composable
fun MapContent() {
    analyticsScreen(name = "map")

    val typography = MaterialTheme.typography()
    val context = ContextAmbient.current

    AndroidView(R.layout.map_view) {
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
