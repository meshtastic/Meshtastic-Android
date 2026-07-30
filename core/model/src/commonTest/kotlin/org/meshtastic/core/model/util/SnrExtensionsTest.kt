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
package org.meshtastic.core.model.util

import org.meshtastic.proto.MeshPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Guards the presence policy for `rx_snr`: absent means null and nothing else. Unlike [rxTimeOrNull], a zero must never
 * be folded into "unknown" — 0 dB is a real measurement. See [snrOrNull].
 *
 * The proto-absent case is not asserted here because it is not yet constructible: `rx_snr` is still a non-null `float`
 * upstream, so [snrOrNull] cannot return null for any packet this test could build. What these tests do lock down is
 * the half that can regress today — that a zero is never folded — which is exactly what breaks if the [rxTimeOrNull]
 * pattern is copied over. Null *handling* is covered where a null is representable: `MetricFormatterTest`
 * (`snrAbsentIsUnknown`) and `LoraSignalIndicatorUiTest` (`snrRendersNothingWhenAbsent`,
 * `loraSignalIndicatorShowsUnknownWhenSnrIsAbsent`).
 */
class SnrExtensionsTest {

    private fun loraPacket(snr: Float) = MeshPacket(
        rx_time = 1_700_000_000,
        rx_snr = snr,
        hop_start = 3,
        hop_limit = 3,
        transport_mechanism = MeshPacket.TransportMechanism.TRANSPORT_LORA,
    )

    @Test
    fun `snrOrNull reports a negative reading`() {
        // The common case: SNR below the noise floor but still demodulable on a long preset.
        assertEquals(-12.5f, loraPacket(-12.5f).snrOrNull())
    }

    @Test
    fun `snrOrNull reports a positive reading`() {
        assertEquals(6.5f, loraPacket(6.5f).snrOrNull())
    }

    @Test
    fun `snrOrNull treats a true zero as a measurement rather than as absent`() {
        // The whole point of the policy. A zero-folding `takeIf { it != 0f }` here would silently discard a signal
        // sitting exactly at the noise floor — comfortably demodulable on every preset — and would also throw away
        // the only zero that can reach us, since a zero from firmware predating the optional conversion is never put
        // on the wire and so already decodes to null.
        val snr = loraPacket(0f).snrOrNull()
        assertNotNull(snr)
        assertEquals(0f, snr)
    }

    @Test
    fun `snrOrNull does not conflate a zero reading with an unknown one`() {
        // Regression guard for the rx_time seam's pattern being copied over verbatim.
        assertEquals(0f, loraPacket(0f).snrOrNull())
        assertEquals(null, MeshPacket().rxTimeOrNull())
    }
}
