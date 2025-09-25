/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.ui.node.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.core.net.toUri
import com.geeksville.mesh.android.BuildUtils.debug
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.HyperlinkBlue
import kotlinx.coroutines.launch
import org.meshtastic.core.model.util.GPSFormat
import java.net.URLEncoder

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkedCoordinates(modifier: Modifier = Modifier, latitude: Double, longitude: Double, nodeName: String) {
    val context = LocalContext.current
    val clipboard: Clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val style =
        SpanStyle(
            color = HyperlinkBlue,
            fontSize = MaterialTheme.typography.labelLarge.fontSize,
            textDecoration = TextDecoration.Underline,
        )

    val annotatedString = rememberAnnotatedString(latitude, longitude, nodeName, style)

    Text(
        modifier =
        modifier.combinedClickable(
            onClick = { handleClick(context, annotatedString) },
            onLongClick = {
                coroutineScope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", annotatedString)))
                    debug("Copied to clipboard")
                }
            },
        ),
        text = annotatedString,
    )
}

@Composable
private fun rememberAnnotatedString(latitude: Double, longitude: Double, nodeName: String, style: SpanStyle) =
    buildAnnotatedString {
        pushStringAnnotation(
            tag = "gps",
            annotation =
            "geo:0,0?q=$latitude,$longitude&z=17&label=${
                URLEncoder.encode(nodeName, "utf-8")
            }",
        )
        withStyle(style = style) {
            val gpsString = GPSFormat.toDec(latitude, longitude)
            append(gpsString)
        }
        pop()
    }

private fun handleClick(context: Context, annotatedString: AnnotatedString) {
    annotatedString.getStringAnnotations(tag = "gps", start = 0, end = annotatedString.length).firstOrNull()?.let {
        val uri = it.item.toUri()
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "No application available to open this location!", Toast.LENGTH_LONG).show()
            }
        } catch (ex: ActivityNotFoundException) {
            debug("Failed to open geo intent: $ex")
        }
    }
}

@PreviewLightDark
@Composable
fun LinkedCoordinatesPreview() {
    AppTheme { LinkedCoordinates(latitude = 37.7749, longitude = -122.4194, nodeName = "Test Node Name") }
}
