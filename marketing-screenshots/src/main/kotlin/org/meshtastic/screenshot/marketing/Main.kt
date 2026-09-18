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
package org.meshtastic.screenshot.marketing

import androidx.compose.runtime.Composable
import java.io.File

/** The map area between the app bar and the bottom navigation, in dp at the phone's 432x864 dp screen. */
private const val MAP_WIDTH_DP = 432
private const val MAP_HEIGHT_DP = 720
private const val NANOS_PER_MILLI = 1_000_000L

/**
 * Renders the five listing screenshots for each requested locale into
 * `<fastlaneMetadata>/<locale>/images/phoneScreenshots/<shot>.png`.
 *
 * Arguments: the `fastlane/metadata/android` directory, then a comma-separated locale list. Two separable steps: every
 * screen is composed offscreen once with [renderScreen] (the map over a single [MapSnapshot] capture), then each is
 * framed per locale with [framePhone]. A desktop form factor is a second caller of the first step.
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: <fastlane/metadata/android dir> <comma-separated locales>" }
    val metadataDir = File(args[0])
    val locales = args[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }

    val t0 = System.nanoTime()
    val mapSnapshot = MapSnapshot.capture(MAP_WIDTH_DP, MAP_HEIGHT_DP, Phone.SCREEN_DENSITY)
    log("map snapshot ${mapSnapshot.width}x${mapSnapshot.height} in ${ms(t0)} ms")

    val screens: Map<Shot, @Composable () -> Unit> =
        mapOf(
            Shot.Messages to { MessagesScreen() },
            Shot.Nodes to { NodesScreen() },
            Shot.Map to { MapScreen(mapSnapshot) },
            Shot.NodeDetail to { NodeDetailScreen() },
            Shot.Channels to { ChannelsScreen() },
        )
    val rendered =
        screens.mapValues { (shot, content) ->
            val t = System.nanoTime()
            val screen = renderScreen(Phone.SCREEN_WIDTH_PX, Phone.SCREEN_HEIGHT_PX, Phone.SCREEN_DENSITY, content)
            log("${shot.fileName}: screen ${screen.width}x${screen.height} in ${ms(t)} ms")
            screen
        }

    for (locale in locales) {
        val captions = Captions.forLocale(locale)
        val outDir = File(metadataDir, "$locale/images/phoneScreenshots").apply { mkdirs() }
        for ((shot, screen) in rendered) {
            val t = System.nanoTime()
            val frame = framePhone(screen, captions.getValue(shot))
            val file = File(outDir, "${shot.fileName}.png")
            frame.writePng(file)
            log("$locale/${file.name}: ${frame.width}x${frame.height} in ${ms(t)} ms")
        }
    }
    log("done in ${ms(t0)} ms")
}

private fun ms(since: Long): Long = (System.nanoTime() - since) / NANOS_PER_MILLI

private fun log(line: String) = System.err.println("[marketing-screenshots] $line")
