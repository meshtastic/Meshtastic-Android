/*
 * Copyright (c) 2026 Meshtastic LLC
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.download
import org.meshtastic.core.resources.view_release
import org.meshtastic.core.ui.icon.Download
import org.meshtastic.core.ui.icon.LinkIcon
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.rememberOpenUrl

@Composable
fun FirmwareReleaseSheetContent(firmwareRelease: FirmwareRelease, modifier: Modifier = Modifier) {
    val openUrl = rememberOpenUrl()

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = firmwareRelease.title, style = MaterialTheme.typography.titleLarge)
        Text(text = "Version: ${firmwareRelease.id}", style = MaterialTheme.typography.bodyMedium)
        Markdown(modifier = Modifier.padding(8.dp), content = firmwareRelease.releaseNotes)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { openUrl(firmwareRelease.pageUrl) }, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = MeshtasticIcons.LinkIcon,
                    contentDescription = stringResource(Res.string.view_release),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(Res.string.view_release))
            }
            Button(onClick = { openUrl(firmwareRelease.zipUrl) }, modifier = Modifier.weight(1f)) {
                Icon(imageVector = MeshtasticIcons.Download, contentDescription = stringResource(Res.string.download))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(Res.string.download))
            }
        }
    }
}
