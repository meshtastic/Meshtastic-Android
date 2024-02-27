package com.geeksville.mesh.ui

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.geeksville.mesh.Position
import com.geeksville.mesh.R
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.ui.theme.HyperlinkBlue
import java.net.URLEncoder

@Composable
fun LinkedCoordinates(
    modifier : Modifier = Modifier,
    position: Position?,
    format: Int,
    nodeName: String?
) {
    if (position?.isValid() == true) {
        val uriHandler = LocalUriHandler.current
        val style = SpanStyle(
            color = HyperlinkBlue,
            fontSize = MaterialTheme.typography.button.fontSize,
            textDecoration = TextDecoration.Underline
        )
        val name = nodeName ?: stringResource(id = R.string.unknown_username)
        val annotatedString = buildAnnotatedString {
            pushStringAnnotation(
                tag = "gps",
                annotation = "geo:${position.latitude},${position.longitude}?z=17&label=${
                    URLEncoder.encode(name, "utf-8")
                }"
            )
            withStyle(style = style) {
                append(position.gpsString(format))
            }
            pop()
        }
        ClickableText(
            modifier = modifier,
            text = annotatedString,
            onClick = { offset ->
                debug("Clicked on link")
                annotatedString.getStringAnnotations(tag = "gps", start = offset, end = offset)
                    .firstOrNull()?.let {
                        uriHandler.openUri(it.item)
                    }
            }
        )
    }
}

@Composable
@Preview
fun LinkedCoordinatesSimplePreview() {
    AppTheme {
        LinkedCoordinates(
            position = Position(37.7749, -122.4194, 0),
            format = 1,
            nodeName = "Test Node Name"
        )
    }
}

@Composable
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
fun LinkedCoordinatesPreview(
    @PreviewParameter(GPSFormatPreviewParameterProvider::class) format: Int
) {
    AppTheme {
        LinkedCoordinates(
            position = Position(37.7749, -122.4194, 0),
            format = format,
            nodeName = "Test Node Name"
        )
    }
}

class GPSFormatPreviewParameterProvider: PreviewParameterProvider<Int> {
    override val values: Sequence<Int>
        get() = sequenceOf(0, 1, 2)
}