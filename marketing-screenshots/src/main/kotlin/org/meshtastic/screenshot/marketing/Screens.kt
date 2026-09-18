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
@file:Suppress("MagicNumber")

package org.meshtastic.screenshot.marketing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.util.getChannelUrl
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.channels
import org.meshtastic.core.resources.edit
import org.meshtastic.core.resources.generate_qr_code
import org.meshtastic.core.resources.map
import org.meshtastic.core.resources.qr_code
import org.meshtastic.core.resources.share_qr_subtext
import org.meshtastic.core.ui.component.AdaptiveTwoPane
import org.meshtastic.core.ui.component.ChannelSelection
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.QrCode
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.rememberQrCodePainter
import org.meshtastic.feature.map.component.MapControlsOverlay
import org.meshtastic.feature.map.component.MapZoomControls
import org.meshtastic.proto.Config

/** Every store screenshot is the dark theme, matching the live listing; dynamic color would follow the host. */
@Composable
internal fun MarketingTheme(content: @Composable () -> Unit) {
    AppTheme(darkTheme = true, dynamicColor = false) { Surface(modifier = Modifier.fillMaxSize(), content = content) }
}

/**
 * The map screen: the real MapLibre snapshot (basemap plus the app's own node chips, captured by [MapSnapshot]) under
 * the map screen's real app bar, toolbar and zoom controls. The library draws its attribution as a MapView overlay,
 * which the snapshotter has no equivalent of, so the same credit line is written here.
 *
 * Composed once per form factor with no [snapshot] to measure the map area - [onMapArea] reports it in pixels - and
 * again with the capture made at exactly that size, so the image is placed without rescaling.
 */
@Composable
internal fun MapScreen(mesh: SampleMesh, snapshot: ImageBitmap?, onMapArea: (IntSize) -> Unit = {}) {
    MarketingTheme {
        AppShell(TopLevelDestination.Map) {
            Scaffold(
                topBar = {
                    MainAppBar(
                        title = stringResource(Res.string.map),
                        ourNode = mesh.baseCamp,
                        showNodeChip = true,
                        canNavigateUp = false,
                        onNavigateUp = {},
                        onClickChip = {},
                        actions = {},
                    )
                },
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding).onSizeChanged(onMapArea)) {
                    if (snapshot != null) {
                        Image(
                            bitmap = snapshot,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    MapControlsOverlay(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                        onToggleFilterMenu = {},
                        onSitePlannerClick = {},
                        onToggleLocationTracking = {},
                    )
                    MapZoomControls(
                        onZoomIn = {},
                        onZoomOut = {},
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    )
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                        color = Color.White.copy(alpha = 0.8f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = "OpenFreeMap © OpenMapTiles Data from OpenStreetMap",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The channel list with the share affordance, in the same [AdaptiveTwoPane] the app's channel screen uses: the rows and
 * the edit button first, the QR second, stacked on a phone and side by side on an expanded window. The real screen
 * shows the QR in a dialog window, which an offscreen scene does not compose, so the same QR painter is laid out
 * inline.
 */
@Composable
internal fun ChannelsScreen(mesh: SampleMesh) {
    val channelSet = mesh.channelSet
    val loraConfig = channelSet.lora_config ?: Config.LoRaConfig.Builder().build()
    val presetName = Channel(loraConfig = loraConfig).name
    MarketingTheme {
        AppShell(TopLevelDestination.Settings) {
            Scaffold(
                topBar = {
                    MainAppBar(
                        title = stringResource(Res.string.channels),
                        ourNode = mesh.baseCamp,
                        showNodeChip = false,
                        canNavigateUp = true,
                        onNavigateUp = {},
                        onClickChip = {},
                        actions = {},
                    )
                },
            ) { padding ->
                AdaptiveTwoPane(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 16.dp),
                    first = {
                        channelSet.settings.forEachIndexed { index, settings ->
                            ChannelSelection(
                                index = index,
                                title = settings.name.ifEmpty { presetName },
                                enabled = true,
                                isSelected = true,
                                onSelected = {},
                                channel = Channel(settings, loraConfig),
                            )
                        }
                        OutlinedButton(
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        ) {
                            Text(text = stringResource(Res.string.edit))
                        }
                    },
                    second = { ShareCard(channelSet.getChannelUrl().toString()) },
                )
            }
        }
    }
}

@Composable
private fun ShareCard(url: String) {
    val qrSizePx = with(LocalDensity.current) { 200.dp.roundToPx() }
    val qrPainter = rememberQrCodePainter(remember(url) { url }, qrSizePx)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = {}, modifier = Modifier.padding(16.dp)) {
            Icon(imageVector = MeshtasticIcons.QrCode, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(Res.string.generate_qr_code))
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = qrPainter,
                    contentDescription = stringResource(Res.string.qr_code),
                    modifier = Modifier.size(200.dp),
                    contentScale = ContentScale.Fit,
                )
                Text(
                    text = stringResource(Res.string.share_qr_subtext),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
