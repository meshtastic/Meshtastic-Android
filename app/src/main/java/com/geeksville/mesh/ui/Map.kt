package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.ui.core.ContextAmbient
import androidx.ui.core.Text
import androidx.ui.fakeandroidview.AndroidView
import androidx.ui.layout.Column
import androidx.ui.material.MaterialTheme
import androidx.ui.tooling.preview.Preview
import com.geeksville.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIState
import com.mapbox.mapboxsdk.maps.MapView

object mapLog : Logging

@Composable
fun MapContent() {
    analyticsScreen(name = "map")

    val typography = MaterialTheme.typography()
    val context = ContextAmbient.current

    Column {
        Text("hi")
        AndroidView(R.layout.map_view) { view ->
            view as MapView
            view.onCreate(UIState.savedInstanceState)
            view.getMapAsync {
                mapLog.info("In getmap")
            }
        }
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
