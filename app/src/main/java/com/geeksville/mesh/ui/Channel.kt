package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.ui.core.Text
import androidx.ui.foundation.DrawImage
import androidx.ui.layout.*
import androidx.ui.material.MaterialTheme
import androidx.ui.res.imageResource
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.geeksville.mesh.R

/// The Compose IDE preview doesn't like the protobufs
data class Channel(val name: String, val num: Int)

@Composable
fun ChannelContent(channel: Channel = Channel("Default", 7)) {
    val typography = MaterialTheme.typography()

    Column(modifier = LayoutSize.Fill + LayoutPadding(16.dp)) {
        Text(
            text = "Channel: ${channel.name}",
            modifier = LayoutGravity.Center,
            style = typography.h4
        )

        Row {
            val image = imageResource(id = R.drawable.qrcode)
            Container(modifier = LayoutGravity.Center + LayoutSize.Min(320.dp, 320.dp)) {
                DrawImage(image = image)
            }
        }

        Text(
            text = "Number: ${channel.num}",
            modifier = LayoutGravity.Center
        )
        Text(
            text = "Mode: Long range (but slow)",
            modifier = LayoutGravity.Center
        )
    }
}


@Preview
@Composable
fun previewChannel() {
    // another bug? It seems modaldrawerlayout not yet supported in preview
    MaterialTheme(colors = palette) {
        ChannelContent()
    }
}
