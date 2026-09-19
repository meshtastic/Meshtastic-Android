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
package org.meshtastic.core.ui.util

import androidx.compose.ui.graphics.Color
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnotatedStringsTest {
    @Test
    fun `traceroute colors the same SNR differently for different presets`() {
        val text = "Node A\n⇊ -15.0 dB\nNode B"
        val longTurbo =
            annotateTraceroute(text, Color.Green, Color.Yellow, Color.Magenta, Color.Red, ModemPreset.LONG_TURBO)
        val shortFast =
            annotateTraceroute(text, Color.Green, Color.Yellow, Color.Magenta, Color.Red, ModemPreset.SHORT_FAST)

        assertEquals(text, longTurbo.text)
        assertEquals(Color.Green, longTurbo.spanStyles.single().item.color)
        assertEquals(Color.Magenta, shortFast.spanStyles.single().item.color)
    }

    @Test
    fun `neighbor info uses the preset and the lowest quality band`() {
        val text = "• Node B (SNR: -15.0)"
        val longTurbo =
            annotateNeighborInfo(text, Color.Green, Color.Yellow, Color.Magenta, Color.Red, ModemPreset.LONG_TURBO)
        val shortFast =
            annotateNeighborInfo(text, Color.Green, Color.Yellow, Color.Magenta, Color.Red, ModemPreset.SHORT_FAST)
        val belowFloor =
            annotateNeighborInfo(
                "• Node B (SNR: -30.0)",
                Color.Green,
                Color.Yellow,
                Color.Magenta,
                Color.Red,
                ModemPreset.LONG_FAST,
            )

        assertEquals(text, longTurbo.text)
        assertEquals(Color.Green, longTurbo.spanStyles.single().item.color)
        assertEquals(Color.Magenta, shortFast.spanStyles.single().item.color)
        assertEquals(Color.Red, belowFloor.spanStyles.single().item.color)
    }

    @Test
    fun `unknown traceroute SNR remains unstyled while zero is a measurement`() {
        val unknown = annotateTraceroute("⇊ ? dB", Color.Green, Color.Yellow, Color.Magenta, Color.Red, null)
        val zero = annotateTraceroute("⇊ 0.0 dB", Color.Green, Color.Yellow, Color.Magenta, Color.Red, null)

        assertEquals("⇊ ? dB", unknown.text)
        assertTrue(unknown.spanStyles.isEmpty())
        assertEquals(Color.Green, zero.spanStyles.single().item.color)
    }
}
