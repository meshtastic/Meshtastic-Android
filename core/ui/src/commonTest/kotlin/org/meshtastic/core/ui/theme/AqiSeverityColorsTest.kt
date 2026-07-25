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
package org.meshtastic.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.meshtastic.core.ui.component.PmAqiSeverity
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins the AQI category palette at WCAG AA text contrast in both themes.
 *
 * The AQI category name is drawn as body text on `surface` (node info cards) and on `surfaceVariant` (metric-log
 * cards), so every tone must clear [MIN_TEXT_CONTRAST] against both. This is why EPA's canonical category hex values
 * are re-toned rather than used verbatim - EPA yellow `#FFFF00` scores 1.01:1 on our light surface.
 */
class AqiSeverityColorsTest {

    private val lightBackgrounds = mapOf("surfaceLight" to surfaceLight, "surfaceVariantLight" to surfaceVariantLight)
    private val darkBackgrounds = mapOf("surfaceDark" to surfaceDark, "surfaceVariantDark" to surfaceVariantDark)

    @Test
    fun everyAqiCategoryToneMeetsAaTextContrastInBothThemes() {
        PmAqiSeverity.entries.forEach { severity ->
            lightBackgrounds.forEach { (name, background) ->
                assertAaText(severity.tones.light, background, severity, name)
            }
            darkBackgrounds.forEach { (name, background) ->
                assertAaText(severity.tones.dark, background, severity, name)
            }
        }
    }

    @Test
    fun lightAndDarkTonesDifferSoNeitherThemeReusesTheOther() {
        // A copy-pasted tone would silently regress one theme; the whole point of the pair is per-theme re-toning.
        PmAqiSeverity.entries.forEach { severity ->
            assertTrue(severity.tones.light != severity.tones.dark, "${severity.name} reuses one tone for both themes")
        }
    }

    @Test
    fun everyCategoryHasItsOwnTonePair() {
        val tones = PmAqiSeverity.entries.map { it.tones }
        assertTrue(tones.distinct().size == tones.size, "two AQI categories share a tone pair: $tones")
    }

    @Test
    fun forcedThemeSelectsThatThemesTone() {
        // color() resolves darkTheme from the applied surface rather than the system setting, so a user forcing light
        // while the OS is dark must still get the light tone that was contrast-checked above.
        PmAqiSeverity.entries.forEach { severity ->
            assertTrue(severity.colorFor(darkTheme = false) == severity.tones.light, "${severity.name} forced light")
            assertTrue(severity.colorFor(darkTheme = true) == severity.tones.dark, "${severity.name} forced dark")
        }
    }

    @Test
    fun staticSurfacesFallOnTheExpectedSideOfTheDarkThresholdColorUses() {
        // color() classifies via MaterialTheme.colorScheme.surface.luminance() < 0.5f; both schemes must land clearly.
        assertTrue(
            surfaceLight.luminance() >= 0.5f,
            "surfaceLight would be classified dark: ${surfaceLight.luminance()}",
        )
        assertTrue(surfaceDark.luminance() < 0.5f, "surfaceDark would be classified light: ${surfaceDark.luminance()}")
    }

    private fun assertAaText(color: Color, background: Color, severity: PmAqiSeverity, backgroundName: String) {
        val ratio = contrastRatio(color, background)
        assertTrue(
            ratio >= MIN_TEXT_CONTRAST,
            "${severity.name} on $backgroundName is $ratio:1, below the $MIN_TEXT_CONTRAST:1 AA text minimum",
        )
    }
}
