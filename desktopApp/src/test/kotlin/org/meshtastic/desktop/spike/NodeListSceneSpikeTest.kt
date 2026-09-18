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

package org.meshtastic.desktop.spike

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.nodes
import org.meshtastic.core.ui.component.NodeItem
import org.meshtastic.core.ui.theme.AppTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SPIKE (throwaway): does a commonMain screen render offscreen through Compose Desktop's [ImageComposeScene]?
 *
 * Writes `build/spike/nodes-<tag>-{1,2}.png` and `build/spike/nodes-<tag>.log`.
 */
class NodeListSceneSpikeTest {

    @Test
    fun `render the node list offscreen`() {
        val tag = SpikeMesh.runTag
        val log = StringBuilder()
        repeat(2) { i ->
            val t0 = System.nanoTime()
            val file = File(SpikeMesh.outDir, "nodes-$tag-${i + 1}.png")
            val frames = renderNodeList(file)
            val took = (System.nanoTime() - t0) / 1_000_000
            log.appendLine("render ${i + 1}: $took ms, $frames frames -> ${file.absolutePath}")
        }
        File(SpikeMesh.outDir, "nodes-$tag.log").writeText(log.toString())
        System.err.println("SPIKE:\n$log")
    }

    /** Renders until the scene settles (bounded), writes the last frame, and returns how many frames it took. */
    private fun renderNodeList(file: File): Int =
        ImageComposeScene(width = 1080, height = 2160, density = Density(2.5f)) { NodeListScreen() }.use { scene ->
            var nanos = 0L
            var frames = 1
            var image: Image = scene.render(nanos)
            while (scene.hasInvalidations() && frames < 20) {
                nanos += 16_000_000L
                image = scene.render(nanos)
                frames++
            }
            assertEquals(1080, image.width)
            assertEquals(2160, image.height)
            file.writeBytes(checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
            frames
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NodeListScreen() {
    val ourNode = SpikeMesh.nodes.first()
    AppTheme(darkTheme = true, dynamicColor = false) {
        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(Res.string.nodes)) }) }) { padding ->
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp)) {
                items(SpikeMesh.nodes.take(8), key = { it.num }) { node ->
                    NodeItem(
                        thisNode = ourNode,
                        thatNode = node,
                        distanceUnits = MeasurementSystem.METRIC,
                        tempInFahrenheit = false,
                        connectionState = ConnectionState.Connected,
                        isActive = node.num == ourNode.num,
                    )
                }
            }
        }
    }
}
