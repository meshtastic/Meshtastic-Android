package com.geeksville.mesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.Position
import com.geeksville.mesh.R
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.ui.theme.HyperlinkBlue
import java.net.URLEncoder

@Composable
fun LinkedCoordinates(
    position: Position?,
    format: Int,
    nodeName: String?
) {
    if (position != null) {
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
            text = annotatedString,
            maxLines = 1,
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
@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
fun LinkedCoordinatesPreview() {
    AppTheme {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) {
                LinkedCoordinates(
                    position = Position(37.7749, -122.4194, 0),
                    format = it,
                    nodeName = "Test Node Name"
                )
            }
        }
    }
}