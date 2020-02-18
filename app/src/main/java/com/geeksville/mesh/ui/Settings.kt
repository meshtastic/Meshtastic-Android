package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.ui.layout.Column
import androidx.ui.layout.LayoutPadding
import androidx.ui.layout.LayoutSize
import androidx.ui.material.MaterialTheme
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.geeksville.android.Logging


object SettingsLog : Logging

@Composable
fun SettingsContent() {
    val typography = MaterialTheme.typography()

    Column(modifier = LayoutSize.Fill + LayoutPadding(16.dp)) {

    }
}


@Preview
@Composable
fun previewSettings() {
    // another bug? It seems modaldrawerlayout not yet supported in preview
    MaterialTheme(colors = palette) {
        SettingsContent()
    }
}
