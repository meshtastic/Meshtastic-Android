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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import java.io.File
import java.util.Locale
import kotlin.math.ceil

private const val NANOS_PER_MILLI = 1_000_000L
private const val FASTLANE_LOCALE = "en-US"
private const val ARG_METADATA_DIR = 0
private const val ARG_BUILD_DIR = 1
private const val ARG_LOCALES = 2
private const val ARG_FRAMED = 3
private const val ARG_COUNT = 4

/**
 * Renders the listing screenshots: every [FormFactors.all] entry's shots, for each requested locale.
 *
 * Arguments: the `fastlane/metadata/android` directory, the build output directory, a comma-separated locale list, and
 * whether to also write the framed phone variants. `en-US` is written to `<fastlane>/en-US/images/<folder>/`; every
 * other locale to `<build>/<locale>/images/<folder>/`, the same layout, for a later `fastlane supply`. Framed variants
 * go to `<build>/framed/<locale>/`.
 *
 * The map is captured first, once per form factor, at the size the map screen lays its map area out at, and reused
 * across locales: neither the basemap nor the chips are localized. Each locale then sets the JVM default locale, which
 * is what the app's own strings, numbers and dates follow, and builds its own [SampleMesh] so the "last heard" labels
 * are measured from a clock a few seconds old, before any of its screens are composed.
 */
fun main(args: Array<String>) {
    require(args.size == ARG_COUNT) {
        "usage: <fastlane/metadata/android dir> <build dir> <comma-separated locales> <framed>"
    }
    val metadataDir = File(args[ARG_METADATA_DIR])
    val buildDir = File(args[ARG_BUILD_DIR])
    val locales = args[ARG_LOCALES].split(',').map { it.trim() }.filter { it.isNotEmpty() }
    require(locales.isNotEmpty()) { "at least one locale is required, got '${args[ARG_LOCALES]}'" }
    val framed = args[ARG_FRAMED].toBooleanStrict()

    val t0 = System.nanoTime()
    val mapAreas = FormFactors.all.associateWith { it.mapArea() }
    val mapSnapshots = MapSnapshot.capture(mapAreas.values.distinct(), SampleMesh())
    log("${mapSnapshots.size} map captures in ${ms(t0)} ms")

    for (locale in locales) {
        Locale.setDefault(Locale.forLanguageTag(locale))
        val mesh = SampleMesh()
        val imagesDir =
            if (locale == FASTLANE_LOCALE) File(metadataDir, "$locale/images") else File(buildDir, "$locale/images")
        for (formFactor in FormFactors.all) {
            val outDir = File(imagesDir, formFactor.folder).apply { mkdirs() }
            val phoneShots = mutableMapOf<Shot, ImageBitmap>()
            for (shot in formFactor.shots) {
                val t = System.nanoTime()
                val snapshot = mapSnapshots.getValue(mapAreas.getValue(formFactor))
                val screen = formFactor.render(screen(shot, mesh, snapshot))
                val file = File(outDir, "${shot.fileName}.png")
                screen.writePng(file)
                log("$locale/${formFactor.folder}/${file.name}: ${screen.width}x${screen.height} in ${ms(t)} ms")
                if (framed && formFactor == FormFactors.phone) phoneShots[shot] = screen
            }
            if (phoneShots.isNotEmpty()) {
                val framedDir = File(buildDir, "framed/$locale").apply { mkdirs() }
                for ((shot, screen) in phoneShots) {
                    val t = System.nanoTime()
                    val frame = framePhone(screen, shot)
                    val file = File(framedDir, "${shot.fileName}.png")
                    frame.writePng(file)
                    log("framed/$locale/${file.name}: ${frame.width}x${frame.height} in ${ms(t)} ms")
                }
            }
        }
    }
    log("done in ${ms(t0)} ms")
}

private fun screen(shot: Shot, mesh: SampleMesh, mapSnapshot: ImageBitmap): @Composable () -> Unit = when (shot) {
    Shot.Messages -> {
        { MessagesScreen(mesh) }
    }

    Shot.Nodes -> {
        { NodesScreen(mesh) }
    }

    Shot.Map -> {
        { MapScreen(mesh, mapSnapshot) }
    }

    Shot.NodeDetail -> {
        { NodeDetailScreen(mesh) }
    }

    Shot.Channels -> {
        { ChannelsScreen(mesh) }
    }
}

private fun FormFactor.render(content: @Composable () -> Unit): ImageBitmap =
    renderScreen(widthPx, heightPx, density, content)

/** Lays the map screen out once with no image and reads back the map area, rounded up to whole dp. */
private fun FormFactor.mapArea(): MapArea {
    var area = IntSize.Zero
    val mesh = SampleMesh()
    render { MapScreen(mesh, snapshot = null, onMapArea = { area = it }) }
    check(area != IntSize.Zero) { "$folder: the map screen laid out no map area" }
    return MapArea(
        widthDp = ceil(area.width / density).toInt(),
        heightDp = ceil(area.height / density).toInt(),
        density = density,
        zoom = mapZoom,
    )
}

private fun ms(since: Long): Long = (System.nanoTime() - since) / NANOS_PER_MILLI

private fun log(line: String) = System.err.println("[marketing-screenshots] $line")
