package com.geeksville.mesh.ui

import android.content.Intent
import androidx.compose.Composable
import androidx.ui.core.ContextAmbient
import androidx.ui.core.Text
import androidx.ui.input.ImeAction
import androidx.ui.layout.*
import androidx.ui.material.MaterialTheme
import androidx.ui.material.OutlinedButton
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.geeksville.analytics.DataPair
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.toHumanString


object ChannelLog : Logging


@Composable
fun ChannelContent(channel: Channel?) {
    analyticsScreen(name = "channel")

    val typography = MaterialTheme.typography()
    val context = ContextAmbient.current

    Column(modifier = LayoutSize.Fill + LayoutPadding(16.dp)) {
        if (channel != null) {
            Row(modifier = LayoutGravity.Center) {

                Text(text = "Channel ", modifier = LayoutGravity.Center)

                if (channel.editable) {
                    // FIXME - limit to max length
                    StyledTextField(
                        value = channel.name,
                        onValueChange = { channel.name = it },
                        textStyle = typography.h4.copy(
                            color = palette.onSecondary.copy(alpha = 0.8f)
                        ),
                        imeAction = ImeAction.Done,
                        onImeActionPerformed = {
                            ChannelLog.errormsg("FIXME, implement channel edit button")
                        }
                    )
                } else {
                    Text(
                        text = channel.name,
                        style = typography.h4
                    )
                }
            }

            // simulated qr code
            // val image = imageResource(id = R.drawable.qrcode)
            val image = AndroidImage(channel.getChannelQR())

            ScaledImage(
                image = image,
                modifier = LayoutGravity.Center + LayoutSize.Min(200.dp, 200.dp)
            )

            Text(
                text = "Mode: ${channel.modemConfig.toHumanString()}",
                modifier = LayoutGravity.Center + LayoutPadding(bottom = 16.dp)
            )

            Row(modifier = LayoutGravity.Center) {

                OutlinedButton(onClick = {
                    channel.editable = !channel.editable
                }) {
                    if (channel.editable)
                        VectorImage(
                            id = R.drawable.ic_twotone_lock_open_24,
                            tint = palette.onBackground
                        )
                    else
                        VectorImage(
                            id = R.drawable.ic_twotone_lock_24,
                            tint = palette.onBackground
                        )
                }

                // Only show the share buttone once we are locked
                if (!channel.editable)
                    OutlinedButton(modifier = LayoutPadding(start = 24.dp),
                        onClick = {
                            GeeksvilleApplication.analytics.track(
                                "share",
                                DataPair("content_type", "channel")
                            ) // track how many times users share channels

                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, channel.getChannelUrl().toString())
                                putExtra(
                                    Intent.EXTRA_TITLE,
                                    "A URL for joining a Meshtastic mesh"
                                )
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
