package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.compose.ambient
import androidx.ui.core.ContextAmbient
import androidx.ui.core.Text
import androidx.ui.foundation.Clickable
import androidx.ui.foundation.DrawImage
import androidx.ui.layout.*
import androidx.ui.material.MaterialTheme
import androidx.ui.material.ripple.Ripple
import androidx.ui.res.imageResource
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.android.toast
import com.geeksville.mesh.R

/// The Compose IDE preview doesn't like the protobufs
data class Channel(val name: String, val num: Int)

object ChannelLog : Logging

@Composable
fun ChannelContent(channel: Channel = Channel("Default", 7)) {
    val typography = MaterialTheme.typography()
    val context = ContextAmbient.current

    Column(modifier = LayoutSize.Fill + LayoutPadding(16.dp)) {
        Text(
            text = "Channel: ${channel.name}",
            modifier = LayoutGravity.Center,
            style = typography.h4
        )

        Row(modifier = LayoutGravity.Center) {
            val image = imageResource(id = R.drawable.qrcode)
            Container(modifier = LayoutGravity.Center + LayoutSize.Min(200.dp, 200.dp)) {
                DrawImage(image = image)
            }

            Ripple(bounded = false) {
                Clickable(onClick = {
                    GeeksvilleApplication.analytics.track("channel_share") // track how many times users share channels
                    context.toast("Channel sharing is not yet implemented")
                }) {
                    VectorImage(
                        id = R.drawable.ic_twotone_share_24,
                        modifier = LayoutGravity.Center + LayoutPadding(left = 8.dp),
                        tint = palette.onBackground
                    )
                }
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
