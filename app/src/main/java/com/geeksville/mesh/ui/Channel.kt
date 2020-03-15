package com.geeksville.mesh.ui

import android.content.Intent
import androidx.compose.Composable
import androidx.ui.core.ContextAmbient
import androidx.ui.core.Text
import androidx.ui.layout.*
import androidx.ui.material.MaterialTheme
import androidx.ui.material.OutlinedButton
import androidx.ui.material.ripple.Ripple
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.geeksville.analytics.DataPair
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.UIState
import com.geeksville.mesh.model.toHumanString


object ChannelLog : Logging


@Composable
fun ChannelContent(channel: Channel?) {
    analyticsScreen(name = "channel")

    val typography = MaterialTheme.typography()
    val context = ContextAmbient.current

    Column(modifier = LayoutSize.Fill + LayoutPadding(16.dp)) {
        if (channel != null) {
            Text(
                text = "Channel: ${channel.name}",
                modifier = LayoutGravity.Center,
                style = typography.h4
            )

            Row(modifier = LayoutGravity.Center) {
                // simulated qr code
                // val image = imageResource(id = R.drawable.qrcode)
                val image = AndroidImage(UIState.getChannelQR(context))

                ScaledImage(
                    image = image,
                    modifier = LayoutGravity.Center + LayoutSize.Min(200.dp, 200.dp)
                )

                Ripple(bounded = false) {
                    OutlinedButton(modifier = LayoutGravity.Center + LayoutPadding(start = 24.dp),
                        onClick = {
                            GeeksvilleApplication.analytics.track(
                                "share",
                                DataPair("content_type", "channel")
                            ) // track how many times users share channels

                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, UIState.getChannelUrl(context))
                                putExtra(Intent.EXTRA_TITLE, "A URL for joining a Meshtastic mesh")
                                type = "text/plain"
                            }

                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        }) {
                        VectorImage(
                            id = R.drawable.ic_twotone_share_24,
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
                text = "Mode: ${channel.modemConfig.toHumanString()}",
                modifier = LayoutGravity.Center
            )
        }
    }
}


@Preview
@Composable
fun previewChannel() {
    // another bug? It seems modaldrawerlayout not yet supported in preview
    MaterialTheme(colors = palette) {
        ChannelContent(Channel.emulated)
    }
}
