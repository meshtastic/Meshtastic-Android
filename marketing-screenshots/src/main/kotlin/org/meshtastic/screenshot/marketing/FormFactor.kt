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
 * One store surface: the pixel size Play wants, the density that puts the app's window class where that device's users
 * see it, and which shots it lists. The screens are form-factor agnostic; only this changes between them.
 *
 * @property folder the `images/<folder>` fastlane writes to. `fastlane supply` and the Play API know phone, sevenInch
 *   and tenInch; chromebook and xr have no upload slot anywhere and are uploaded by hand in Play Console.
 * @property mapZoom the map shot's zoom: the 1x layouts have far more room in both directions, so they sit half a level
 *   closer without any of the nine chips leaving the frame.
 */
internal data class FormFactor(
    val folder: String,
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val mapZoom: Double,
    val shots: List<Shot> = Shot.entries,
)

internal object FormFactors {
    /** 1080x1920 at 2.5x is 432x768 dp: a compact window, so the app lays out its bottom navigation bar. */
    val phone = FormFactor("phoneScreenshots", 1080, 1920, 2.5f, 11.5)

    /**
     * 7-inch portrait. 1080x1920 at 1.8x is 600x1067 dp: the medium width class, where the app switches to a navigation
     * rail but keeps one pane. Play's tablet rules want 9:16; a Nexus 7's own 1200x1920 is 5:8.
     */
    val sevenInch = FormFactor("sevenInchScreenshots", 1080, 1920, 1.8f, 11.5)

    /**
     * 10-inch landscape. 2560x1440 at 2x is 1280x720 dp: expanded, so list-detail and two-pane layouts split. Its map
     * area is the shortest of the five, so it keeps the phone's zoom; one level closer puts the river node off the
     * bottom edge.
     */
    val tenInch = FormFactor("tenInchScreenshots", 2560, 1440, 2f, 11.5)

    /** A Chromebook at 1x: 1920x1080 dp, expanded. No supply or API slot; hand-uploaded in Play Console. */
    val chromebook = FormFactor("chromebookScreenshots", 1920, 1080, 1f, 12.0)

    /** Android XR's 8:5 panel at 1x: 1920x1200 dp, expanded, the same shell as the Chromebook. Hand-uploaded. */
    val xr = FormFactor("xrScreenshots", 1920, 1200, 1f, 12.0)

    val all = listOf(phone, sevenInch, tenInch, chromebook, xr)
}
