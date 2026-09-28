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

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertTrue

/** Pins the Link colour at WCAG AA text contrast on each scheme's surface, which no single blue manages. */
class LinkColorTest {

    @Test
    fun linkMeetsAaTextContrastOnTheLightSurface() {
        assertAaText(lightColorScheme(surface = surfaceLight).link, surfaceLight)
    }

    @Test
    fun linkMeetsAaTextContrastOnTheLightDialogBackground() {
        assertAaText(lightColorScheme(surface = surfaceLight).link, surfaceContainerHighLight)
    }

    @Test
    fun linkMeetsAaTextContrastOnTheDarkSurface() {
        assertAaText(darkColorScheme(surface = surfaceDark).link, surfaceDark)
    }

    @Test
    fun linkMeetsAaTextContrastOnTheDarkDialogBackground() {
        assertAaText(darkColorScheme(surface = surfaceDark).link, surfaceContainerHighDark)
    }

    private fun assertAaText(link: Color, surface: Color) {
        val ratio = contrastRatio(link, surface)
        assertTrue(ratio >= MIN_TEXT_CONTRAST, "link $link on $surface is $ratio:1, below $MIN_TEXT_CONTRAST:1")
    }
}
