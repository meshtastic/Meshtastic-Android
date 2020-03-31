package com.geeksville.mesh.ui

import android.graphics.Bitmap
import androidx.compose.Composable
import androidx.ui.core.DensityAmbient
import androidx.ui.core.DrawModifier
import androidx.ui.core.Modifier
import androidx.ui.core.asModifier
import androidx.ui.foundation.Box
import androidx.ui.graphics.*
import androidx.ui.graphics.colorspace.ColorSpaces
import androidx.ui.graphics.painter.ImagePainter
import androidx.ui.unit.Density
import androidx.ui.unit.PxSize
import androidx.ui.unit.toRect

/// Stolen from the Compose SimpleImage, replace with their real Image component someday
// TODO(mount, malkov) : remove when RepaintBoundary is a modifier: b/149982905
// This is class and not val because if b/149985596
private object ClipModifier : DrawModifier {
    override fun draw(density: Density, drawContent: () -> Unit, canvas: Canvas, size: PxSize) {
        canvas.save()
        canvas.clipRect(size.toRect())
        drawContent()
        canvas.restore()
    }
}


/// Stolen from the Compose SimpleImage, replace with their real Image component someday
@Composable
fun ScaledImage(
    image: ImageAsset,
    modifier: Modifier = Modifier.None,
    tint: Color? = null
) {
    with(DensityAmbient.current) {
        val imageModifier = ImagePainter(image).asModifier(
            scaleFit = ScaleFit.FillMaxDimension,
            colorFilter = tint?.let { ColorFilter(it, BlendMode.srcIn) }
        )
        Box(modifier + ClipModifier + imageModifier)
    }
}


/// Borrowed from Compose
class AndroidImage(val bitmap: Bitmap) : ImageAsset {

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

    override val config: ImageAssetConfig get() = ImageAssetConfig.Argb8888

    /**
     * @see Image.colorSpace
     */
    override val colorSpace: androidx.ui.graphics.colorspace.ColorSpace
        get() = ColorSpaces.Srgb

    /**
     * @see Image.hasAlpha
     */
    override val hasAlpha: Boolean
        get() = bitmap.hasAlpha()

    /**
     * @see Image.nativeImage
     */
    override val nativeImage: NativeImageAsset
        get() = bitmap

    /**
     * @see
     */
    override fun prepareToDraw() {
        bitmap.prepareToDraw()
    }
}
