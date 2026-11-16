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
import org.meshtastic.core.ui.component.Iaq
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The IAQ tile draws the value as body text on `surfaceContainerLow` (the info card), and the metric log on `surface`
 * and `surfaceVariant`. The Bosch fill colors are for white-on-pill and fail as text, so the band text tones must clear
 * [MIN_TEXT_CONTRAST] on all of them in both themes.
 */
class IaqColorsTest {

    private val lightBackgrounds =
        mapOf(
            "surfaceLight" to surfaceLight,
            "surfaceVariantLight" to surfaceVariantLight,
            "surfaceContainerLowLight" to surfaceContainerLowLight,
        )
    private val darkBackgrounds =
        mapOf(
            "surfaceDark" to surfaceDark,
            "surfaceVariantDark" to surfaceVariantDark,
            "surfaceContainerLowDark" to surfaceContainerLowDark,
        )

    @Test
    fun everyIaqBandTextToneMeetsAaContrastInBothThemes() {
        Iaq.entries.forEach { band ->
            lightBackgrounds.forEach { (name, bg) ->
                assertAaText(band.textColorFor(darkTheme = false), bg, band, name)
            }
            darkBackgrounds.forEach { (name, bg) -> assertAaText(band.textColorFor(darkTheme = true), bg, band, name) }
        }
    }

    @Test
    fun everyBandHasItsOwnTonePairAndEachThemeDiffers() {
        val tones = Iaq.entries.map { it.tones }
        assertEquals(tones.size, tones.distinct().size, "two IAQ bands share a tone pair")
        Iaq.entries.forEach {
            assertTrue(it.tones.light != it.tones.dark, "${it.name} reuses one tone for both themes")
        }
    }

    private fun assertAaText(color: Color, background: Color, band: Iaq, backgroundName: String) {
        val ratio = contrastRatio(color, background)
        assertTrue(
            ratio >= MIN_TEXT_CONTRAST,
            "${band.name} on $backgroundName is $ratio:1, below $MIN_TEXT_CONTRAST:1",
        )
    }
}
