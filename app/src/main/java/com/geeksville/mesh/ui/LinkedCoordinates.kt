package com.geeksville.mesh.ui

import android.content.ActivityNotFoundException
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig.GpsCoordinateFormat
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.ui.theme.HyperlinkBlue
import com.geeksville.mesh.util.GPSFormat
import java.net.URLEncoder

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkedCoordinates(
    modifier: Modifier = Modifier,
    latitude: Double,
    longitude: Double,
    format: Int,
    nodeName: String,
) {
    val uriHandler = LocalUriHandler.current
    val style = SpanStyle(
        color = HyperlinkBlue,
        fontSize = MaterialTheme.typography.button.fontSize,
        textDecoration = TextDecoration.Underline
    )
    val annotatedString = buildAnnotatedString {
        pushStringAnnotation(
            tag = "gps",
            // URI scheme is defined at:
            //  https://developer.android.com/guide/components/intents-common#Maps
            annotation = "geo:0,0?q=${latitude},${longitude}&z=17&label=${
                URLEncoder.encode(nodeName, "utf-8")
            }"
        )
        withStyle(style = style) {
            val gpsString = when (format) {
                GpsCoordinateFormat.DEC_VALUE -> GPSFormat.toDEC(latitude, longitude)
                GpsCoordinateFormat.DMS_VALUE -> GPSFormat.toDMS(latitude, longitude)
                GpsCoordinateFormat.UTM_VALUE -> GPSFormat.toUTM(latitude, longitude)
                GpsCoordinateFormat.MGRS_VALUE -> GPSFormat.toMGRS(latitude, longitude)
                else -> GPSFormat.toDEC(latitude, longitude)
            }
            append(gpsString)
        }
        pop()
    }
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    Text(
        modifier = modifier.combinedClickable(
            onClick = {
                annotatedString.getStringAnnotations(
                    tag = "gps",
                    start = 0,
                    end = annotatedString.length
                ).firstOrNull()?.let {
                    try {
                        uriHandler.openUri(it.item)
                    } catch (ex: ActivityNotFoundException) {
                        debug("No application found: $ex")
                    }
                }
            },
            onLongClick = {
                clipboardManager.setText(annotatedString)
                debug("Copied to clipboard")
            }
        ),
        text = annotatedString
    )
}

@PreviewLightDark
@Composable
fun LinkedCoordinatesPreview(
    @PreviewParameter(GPSFormatPreviewParameterProvider::class) format: Int
) {
    AppTheme {
        LinkedCoordinates(
            latitude = 37.7749,
            longitude = -122.4194,
            format = format,
            nodeName = "Test Node Name"
        )
    }
}

class GPSFormatPreviewParameterProvider : PreviewParameterProvider<Int> {
    override val values: Sequence<Int>
        get() = sequenceOf(0, 1, 2)
}
