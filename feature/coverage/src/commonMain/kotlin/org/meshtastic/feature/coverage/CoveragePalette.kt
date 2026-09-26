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
package org.meshtastic.feature.coverage

import kotlin.math.roundToInt

/**
 * The colour ramps the Site Planner's Display section offers, by the names it uses.
 *
 * These are the matplotlib colormaps the hosted planner renders with, carried here so the local engine honours the same
 * picker rather than inventing its own colours. Each is stored as evenly spaced anchors and interpolated between: a
 * handful of stops per ramp reproduces the perceptual shape closely enough for six discrete coverage bands, without
 * embedding a 256-entry table each.
 */
@Suppress("MagicNumber") // Colour anchors are data; naming forty hex literals would obscure the ramps.
enum class CoveragePalette(val key: String, private val anchors: List<Int>) {
    PLASMA("plasma", listOf(0x0D0887, 0x6A00A8, 0xB12A90, 0xE16462, 0xFCA636, 0xF0F921)),
    VIRIDIS("viridis", listOf(0x440154, 0x414487, 0x2A788E, 0x22A884, 0x7AD151, 0xFDE725)),
    CMR_MAP("CMRmap", listOf(0x000000, 0x3B2277, 0xA1417F, 0xE1663A, 0xE6AE3E, 0xD7D7A0, 0xFFFFFF)),
    COOL("cool", listOf(0x00FFFF, 0xFF00FF)),
    TURBO(
        "turbo",
        listOf(0x30123B, 0x4145AB, 0x4675ED, 0x39A2FC, 0x1BCFD4, 0x62FC6B, 0xD2E935, 0xFDA007, 0xF05B12, 0x7A0403),
    ),
    JET("jet", listOf(0x000080, 0x0000FF, 0x00FFFF, 0x7FFF7F, 0xFFFF00, 0xFF0000, 0x800000)),
    ;

    /** The ramp sampled at [t] in 0..1, as a `#rrggbb` string. */
    fun colorAt(t: Double): String {
        val clamped = t.coerceIn(0.0, 1.0)
        if (anchors.size == 1) return hex(anchors[0])

        val position = clamped * (anchors.size - 1)
        val lower = position.toInt().coerceAtMost(anchors.size - 2)
        val fraction = position - lower
        return hex(mix(anchors[lower], anchors[lower + 1], fraction))
    }

    private fun mix(from: Int, to: Int, fraction: Double): Int {
        val r = channel(from, RED_SHIFT) + (channel(to, RED_SHIFT) - channel(from, RED_SHIFT)) * fraction
        val g = channel(from, GREEN_SHIFT) + (channel(to, GREEN_SHIFT) - channel(from, GREEN_SHIFT)) * fraction
        val b = channel(from, BLUE_SHIFT) + (channel(to, BLUE_SHIFT) - channel(from, BLUE_SHIFT)) * fraction
        return (r.roundToInt() shl RED_SHIFT) or (g.roundToInt() shl GREEN_SHIFT) or b.roundToInt()
    }

    private fun channel(color: Int, shift: Int): Double = ((color shr shift) and CHANNEL_MASK).toDouble()

    private fun hex(color: Int): String = buildString {
        append('#')
        for (shift in listOf(RED_SHIFT, GREEN_SHIFT, BLUE_SHIFT)) {
            val value = (color shr shift) and CHANNEL_MASK
            append(HEX_DIGITS[value shr NIBBLE_BITS])
            append(HEX_DIGITS[value and NIBBLE_MASK])
        }
    }

    companion object {
        /** The palette the planner names, or [PLASMA] — its own default — when the name is unknown. */
        fun forKey(key: String?): CoveragePalette =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: PLASMA

        private const val RED_SHIFT = 16
        private const val GREEN_SHIFT = 8
        private const val BLUE_SHIFT = 0
        private const val CHANNEL_MASK = 0xFF
        private const val NIBBLE_BITS = 4
        private const val NIBBLE_MASK = 0xF
        private val HEX_DIGITS = "0123456789abcdef".toCharArray()
    }
}

/**
 * How a [CoverageGrid] is drawn — the planner's Display section, which the local engine used to ignore entirely while
 * the sheet still offered it.
 *
 * [minDbm] and [maxDbm] fix the ends of the ramp rather than letting the strongest cell define them, so two sites are
 * comparable and the picker means what it says.
 */
class CoverageStyle(
    val palette: CoveragePalette = CoveragePalette.PLASMA,
    val minDbm: Double = DEFAULT_MIN_DBM,
    val maxDbm: Double = DEFAULT_MAX_DBM,
    /** 0 is invisible, 1 is solid — the planner's transparency percentage inverted. */
    val opacity: Double = DEFAULT_OPACITY,
) {
    init {
        require(maxDbm > minDbm) { "maxDbm must exceed minDbm, got $minDbm..$maxDbm" }
    }

    companion object {
        const val DEFAULT_MIN_DBM = -130.0
        const val DEFAULT_MAX_DBM = -80.0
        const val DEFAULT_OPACITY = 0.5

        /** From the planner's 0–100 transparency, where 0 is opaque. */
        fun fromTransparency(
            palette: String?,
            minDbm: Double,
            maxDbm: Double,
            transparencyPercent: Int,
        ): CoverageStyle = CoverageStyle(
            palette = CoveragePalette.forKey(palette),
            minDbm = minDbm,
            maxDbm = maxDbm,
            opacity = 1.0 - (transparencyPercent.coerceIn(0, PERCENT) / PERCENT.toDouble()),
        )

        private const val PERCENT = 100
    }
}
