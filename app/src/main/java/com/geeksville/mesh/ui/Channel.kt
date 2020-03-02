package com.geeksville.mesh.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.Composable
import androidx.ui.core.ContextAmbient
import androidx.ui.core.Text
import androidx.ui.foundation.DrawImage
import androidx.ui.graphics.Image
import androidx.ui.graphics.ImageConfig
import androidx.ui.graphics.NativeImage
import androidx.ui.graphics.colorspace.ColorSpace
import androidx.ui.graphics.colorspace.ColorSpaces
import androidx.ui.layout.*
import androidx.ui.material.MaterialTheme
import androidx.ui.material.OutlinedButton
import androidx.ui.material.ripple.Ripple
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIState

/// The Compose IDE preview doesn't like the protobufs
data class Channel(val name: String, val num: Int)

object ChannelLog : Logging

/// Borrowed from Compose
class AndroidImage(val bitmap: Bitmap) : Image {

    /**
     * @see Image.width
     */
    override val width: Int
        get() = bitmap.width

    /**
     * @see Image.height
     */
    override val height: Int
        get() = bitmap.height

    override val config: ImageConfig get() = ImageConfig.Argb8888

    /**
     * @see Image.colorSpace
     */
    override val colorSpace: ColorSpace
        get() = ColorSpaces.Srgb

    /**
     * @see Image.hasAlpha
     */
    override val hasAlpha: Boolean
        get() = bitmap.hasAlpha()

    /**
     * @see Image.nativeImage
     */
    override val nativeImage: NativeImage
        get() = bitmap

    /**
     * @see
     */
    override fun prepareToDraw() {
        bitmap.prepareToDraw()
    }
}

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
            // simulated qr code
            // val image = imageResource(id = R.drawable.qrcode)
            val image = AndroidImage(UIState.getChannelQR(context))

            Container(modifier = LayoutGravity.Center + LayoutSize.Min(200.dp, 200.dp)) {
                DrawImage(image = image)
            }

            Ripple(bounded = false) {
                OutlinedButton(modifier = LayoutGravity.Center + LayoutPadding(left = 24.dp),
                    onClick = {
                        GeeksvilleApplication.analytics.track("channel_share") // track how many times users share channels

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
