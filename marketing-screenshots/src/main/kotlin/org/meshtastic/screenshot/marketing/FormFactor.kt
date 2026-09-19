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

/**
 * One listing surface: the pixel size the store wants, the density that puts the app's window class where that device's
 * users see it, which shots it lists and where they go. The screens are surface-agnostic; only this changes between
 * them.
 *
 * @property name the log label, and the fastlane folder for the five Play surfaces.
 * @property shots the shots in listing order; [naming] turns a position in it into a file name.
 * @property committedDir where the `en-US` set lands, relative to the repository root - the one tracked in git.
 * @property localeDir where every other locale lands, relative to `<build>/<locale>/`.
 * @property mapZoom the map shot's zoom: the 1x layouts have far more room in both directions, so they sit half a level
 *   closer without any of the nine chips leaving the frame.
 */
internal data class FormFactor(
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val mapZoom: Double,
    val shots: List<Shot>,
    val naming: FileNaming,
    val committedDir: String,
    val localeDir: String,
) {
    fun fileName(shot: Shot): String = naming.fileName(shots.indexOf(shot), shot)
}

/** How a form factor names its files: each surface's consumer reads a different pattern off the disk. */
internal enum class FileNaming {
    /** `1_messages`: Play and F-Droid sort the listing by file name. */
    Fastlane {
        override fun fileName(index: Int, shot: Shot): String = "${index + 1}_${shot.slug}"
    },

    /** `meshtastic-desktop-01-nodes`: the names the Flathub metainfo's `<image>` URLs point at. */
    Desktop {
        override fun fileName(index: Int, shot: Shot): String =
            "meshtastic-desktop-${(index + 1).toString().padStart(2, '0')}-${shot.slug}"
    }, ;

    abstract fun fileName(index: Int, shot: Shot): String
}

internal object FormFactors {
    /** The Play listing, in listing order. */
    private val playShots = listOf(Shot.Messages, Shot.Nodes, Shot.Map, Shot.NodeDetail, Shot.Channels)

    /**
     * A Play surface: `fastlane supply` and the Play API know phone, sevenInch and tenInch; chromebook and xr have no
     * upload slot anywhere and are uploaded by hand in Play Console. Only `en-US` is committed, because everything
     * under `fastlane/` is read straight from git by F-Droid and IzzyOnDroid.
     */
    private fun play(folder: String, widthPx: Int, heightPx: Int, density: Float, mapZoom: Double) = FormFactor(
        name = folder,
        widthPx = widthPx,
        heightPx = heightPx,
        density = density,
        mapZoom = mapZoom,
        shots = playShots,
        naming = FileNaming.Fastlane,
        committedDir = "fastlane/metadata/android/en-US/images/$folder",
        localeDir = "images/$folder",
    )

    /** 1080x1920 at 2.5x is 432x768 dp: a compact window, so the app lays out its bottom navigation bar. */
    val phone = play("phoneScreenshots", 1080, 1920, 2.5f, 11.5)

    /**
     * 7-inch portrait. 1080x1920 at 1.8x is 600x1067 dp: the medium width class, where the app switches to a navigation
     * rail but keeps one pane. Play's tablet rules want 9:16; a Nexus 7's own 1200x1920 is 5:8.
     */
    val sevenInch = play("sevenInchScreenshots", 1080, 1920, 1.8f, 11.5)

    /**
     * 10-inch landscape. 2560x1440 at 2x is 1280x720 dp: expanded, so list-detail and two-pane layouts split. Its map
     * area is the shortest of the Play five, so it keeps the phone's zoom; one level closer puts the river node off the
     * bottom edge.
     */
    val tenInch = play("tenInchScreenshots", 2560, 1440, 2f, 11.5)

    /** A Chromebook at 1x: 1920x1080 dp, expanded. No supply or API slot; hand-uploaded in Play Console. */
    val chromebook = play("chromebookScreenshots", 1920, 1080, 1f, 12.0)

    /** Android XR's 8:5 panel at 1x: 1920x1200 dp, expanded, the same shell as the Chromebook. Hand-uploaded. */
    val xr = play("xrScreenshots", 1920, 1200, 1f, 12.0)

    /**
     * The desktop app's Flathub listing: 1280x800 at 1x, AppStream's recommended 16:10, expanded, the same shell as the
     * Chromebook. Its map area is the 10-inch's plus 80 dp, too little for the closer zoom. The metainfo lists the
     * files by commit-pinned URL, so a regenerated set is two commits: the files, then the URLs.
     */
    val desktop =
        FormFactor(
            name = "desktop",
            widthPx = 1280,
            heightPx = 800,
            density = 1f,
            mapZoom = 11.5,
            shots = listOf(Shot.Nodes, Shot.Messages, Shot.Map, Shot.Connections, Shot.Settings),
            naming = FileNaming.Desktop,
            committedDir = "desktopApp/packaging/linux/screenshots",
            localeDir = "desktop",
        )

    val all = listOf(phone, sevenInch, tenInch, chromebook, xr, desktop)
}
