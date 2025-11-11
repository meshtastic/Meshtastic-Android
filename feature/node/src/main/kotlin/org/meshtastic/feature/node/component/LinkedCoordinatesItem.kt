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

package org.meshtastic.feature.node.component

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.util.GPSFormat
import org.meshtastic.core.model.util.formatAgo
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.last_position_update
import org.meshtastic.core.ui.component.BasicListItem
import org.meshtastic.core.ui.component.icon
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.showToast
import timber.log.Timber
import java.net.URLEncoder

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkedCoordinatesItem(node: Node) {
    val context = LocalContext.current
    val clipboard: Clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    val ago = formatAgo(node.position.time)
    val coordinates = GPSFormat.toDec(node.latitude, node.longitude)

    BasicListItem(
        text = stringResource(Res.string.last_position_update),
        leadingIcon = Icons.Default.LocationOn,
        supportingText = "$ago • $coordinates",
        trailingContent = Icons.AutoMirrored.Rounded.KeyboardArrowRight.icon(),
        onClick = {
            val label = URLEncoder.encode(node.user.longName, "utf-8")
            val uri = "geo:0,0?q=${node.latitude},${node.longitude}&z=17&label=$label".toUri()
            val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    coroutineScope.launch { context.showToast("No application available to open this location!") }
                }
            } catch (ex: ActivityNotFoundException) {
                Timber.d("Failed to open geo intent: $ex")
            }
        },
        onLongClick = {
            coroutineScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", coordinates))) }
        },
    )
}

@PreviewLightDark
@Composable
private fun LinkedCoordinatesPreview() {
    AppTheme { LinkedCoordinatesItem(Node(0)) }
}
