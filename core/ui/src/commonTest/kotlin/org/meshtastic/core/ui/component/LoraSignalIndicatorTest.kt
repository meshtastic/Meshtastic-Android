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
package org.meshtastic.core.ui.component

import org.meshtastic.core.model.snrLimit
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for preset-relative signal-quality rating (issue #5446). Quality is judged from SNR relative to the modem
 * preset's demodulation floor. RSSI only joins the rating when a noise floor is also known (design#15, #6826) — see the
 * "noise-floor blend" tests below; with either missing, RSSI stays display-only.
 */
class LoraSignalIndicatorTest {

    @Test
    fun `snrLimit follows spreading-factor demod floor per preset`() {
        assertEquals(-7.5f, ModemPreset.SHORT_FAST.snrLimit) // SF7
        assertEquals(-7.5f, ModemPreset.SHORT_TURBO.snrLimit) // SF7
        assertEquals(-10f, ModemPreset.SHORT_SLOW.snrLimit) // SF8
        assertEquals(-12.5f, ModemPreset.MEDIUM_FAST.snrLimit) // SF9
        assertEquals(-15f, ModemPreset.MEDIUM_SLOW.snrLimit) // SF10
        assertEquals(-17.5f, ModemPreset.LONG_FAST.snrLimit) // SF11
        assertEquals(-17.5f, ModemPreset.LONG_MODERATE.snrLimit) // SF11
    }

    @Test
    fun `LONG_SLOW uses physically-correct SF12 floor`() {
        // Meshtastic-Apple's snrLimit() returns -7.5 here (the SF7 value, an apparent bug). The correct SF12
        // demodulation floor is -20 dB — see #5446.
        assertEquals(-20f, ModemPreset.LONG_SLOW.snrLimit)
        assertEquals(-20f, ModemPreset.VERY_LONG_SLOW.snrLimit)
    }

    @Test
    fun `null preset falls back to the LongFast default limit`() {
        val noPreset: ModemPreset? = null
        assertEquals(-17.5f, noPreset.snrLimit)
    }

    @Test
    fun `the issue's example minus 10 dB SNR rates GOOD on LongFast`() {
        // The bug this issue fixes: a fixed threshold rated -10 dB as BAD, but on LongFast (floor -17.5) it is
        // 7.5 dB above the demod floor — an excellent signal.
        assertEquals(Quality.GOOD, determineSignalQuality(snr = -10f, modemPreset = ModemPreset.LONG_FAST))
    }

    @Test
    fun `the same SNR is rated relative to the preset`() {
        // -15 dB is comfortably above LongSlow's -20 floor but at/below ShortFast's -7.5 floor.
        assertEquals(Quality.GOOD, determineSignalQuality(snr = -15f, modemPreset = ModemPreset.LONG_SLOW))
        assertEquals(Quality.BAD, determineSignalQuality(snr = -15f, modemPreset = ModemPreset.SHORT_FAST))
    }

    @Test
    fun `quality bands around the LongFast floor`() {
        // limit = -17.5; FAIR within 5.5 dB below, BAD within 7.5 dB below, NONE beyond.
        val preset = ModemPreset.LONG_FAST
        assertEquals(Quality.GOOD, determineSignalQuality(snr = -17f, modemPreset = preset)) // > limit
        assertEquals(Quality.FAIR, determineSignalQuality(snr = -17.5f, modemPreset = preset)) // at limit
        assertEquals(Quality.FAIR, determineSignalQuality(snr = -22f, modemPreset = preset)) // > limit-5.5 (-23)
        assertEquals(Quality.BAD, determineSignalQuality(snr = -23f, modemPreset = preset)) // >= limit-7.5 (-25)
        assertEquals(Quality.NONE, determineSignalQuality(snr = -30f, modemPreset = preset)) // < limit-7.5
    }

    @Test
    fun `a zero SNR reading is rated rather than treated as missing`() {
        // 0 dB sits well above every preset's demod floor, so it is an excellent signal — not an absent one. If a
        // presence check ever folds zero into "unknown", this is the reading that disappears.
        assertEquals(Quality.GOOD, determineSignalQuality(snr = 0f, modemPreset = ModemPreset.LONG_FAST))
        assertEquals(Quality.GOOD, determineSignalQuality(snr = 0f, modemPreset = ModemPreset.SHORT_FAST))
    }

    @Test
    fun `absent SNR is not a quality band`() {
        // Quality has no member for "no measurement": callers must pass a non-null SNR, and the composables render
        // absence as Unknown rather than mapping it onto NONE (which asserts a measured, undemodulable signal).
        assertEquals(4, Quality.entries.size)
        assertEquals(listOf(Quality.NONE, Quality.BAD, Quality.FAIR, Quality.GOOD), Quality.entries.toList())
    }

    @Test
    fun `RSSI alone does not influence the rating`() {
        // Without a noise floor, identical SNR + preset always yields the same verdict regardless of any RSSI.
        val withoutNoiseFloor = determineSignalQuality(snr = -5f, modemPreset = ModemPreset.LONG_FAST, rssi = -140)
        assertEquals(Quality.GOOD, withoutNoiseFloor)
    }

    @Test
    fun `noise floor alone does not influence the rating`() {
        // Without an RSSI reading, a noise floor alone can't form a margin either.
        val withoutRssi = determineSignalQuality(snr = -5f, modemPreset = ModemPreset.LONG_FAST, noiseFloor = -70)
        assertEquals(Quality.GOOD, withoutRssi)
    }

    @Test
    fun `noise-floor blend picks the worse tier when RSSI margin is worse than SNR margin`() {
        val preset = ModemPreset.LONG_FAST // limit -17.5
        // SNR -10 -> margin +7.5 -> GOOD alone. rssi(-90) - noiseFloor(-70) = -20; margin -2.5 -> FAIR, the worse tier.
        val quality = determineSignalQuality(snr = -10f, modemPreset = preset, rssi = -90, noiseFloor = -70)
        assertEquals(Quality.FAIR, quality)
    }

    @Test
    fun `same SNR rates differently depending on the RSSI margin alone`() {
        // Differential proof the blend is actually wired in, not just accepting-and-ignoring rssi/noiseFloor: hold
        // SNR fixed at a FAIR-alone reading (-22, margin -4.5 on LongFast's -17.5 limit) and vary only rssi/noiseFloor.
        val preset = ModemPreset.LONG_FAST
        val snr = -22f

        // rssi(-94) - noiseFloor(-70) = -24; margin -6.5 -> BAD, worse than SNR's own FAIR -> downgrades the result.
        val withBadRssiMargin = determineSignalQuality(snr = snr, modemPreset = preset, rssi = -94, noiseFloor = -70)
        assertEquals(Quality.BAD, withBadRssiMargin)

        // rssi(-60) - noiseFloor(-70) = 10; margin +27.5 -> GOOD, better than SNR's FAIR -> SNR stays the worse tier.
        val withGoodRssiMargin = determineSignalQuality(snr = snr, modemPreset = preset, rssi = -60, noiseFloor = -70)
        assertEquals(Quality.FAIR, withGoodRssiMargin)
    }

    @Test
    fun `noise-floor blend can downgrade a good SNR reading all the way to NONE`() {
        val preset = ModemPreset.LONG_FAST
        // SNR -10 -> margin +7.5 -> GOOD alone. rssi(-115) - noiseFloor(-70) = -45; margin -27.5 -> NONE.
        val quality = determineSignalQuality(snr = -10f, modemPreset = preset, rssi = -115, noiseFloor = -70)
        assertEquals(Quality.NONE, quality)
    }
}
