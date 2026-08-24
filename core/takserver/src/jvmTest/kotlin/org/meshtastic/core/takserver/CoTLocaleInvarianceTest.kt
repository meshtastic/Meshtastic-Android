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
package org.meshtastic.core.takserver

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CoT remarks are read by other TAK clients, so their numbers stay invariant while the app's own display follows the
 * locale. The rest of the suite runs under the build's pinned en-US locale, where the invariant and localized
 * formatters produce identical output — so only a comma locale can tell them apart, and only this test does.
 */
class CoTLocaleInvarianceTest {

    private val originalLocale: Locale = Locale.getDefault()

    @AfterTest
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `remarks keep dot decimals and no grouping on a comma locale`() {
        Locale.setDefault(Locale.GERMANY)

        val remarks =
            MeshNodeCoTConversionTest.meshNode(voltage = 3.9f, snr = 8.5f, channelUtilization = 1234.5f).cotRemarks()

        assertTrue(remarks!!.contains("3.90V"), "voltage was localized: $remarks")
        assertTrue(remarks.contains("8.5 dB"), "SNR was localized: $remarks")
        // Grouping would make this "1.234,5" on de-DE and split the field for anything parsing it.
        assertTrue(remarks.contains("1234.5%"), "channel utilization was grouped or localized: $remarks")
        assertFalse(remarks.contains(","), "a comma reached the payload: $remarks")
    }
}
