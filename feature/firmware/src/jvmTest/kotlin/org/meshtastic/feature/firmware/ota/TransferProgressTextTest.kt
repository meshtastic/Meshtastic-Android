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
package org.meshtastic.feature.firmware.ota

import kotlinx.coroutines.test.runTest
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The progress text as the screen resolves it from string resources. */
class TransferProgressTextTest {

    private lateinit var originalLocale: Locale

    @BeforeTest
    fun setUp() {
        originalLocale = Locale.getDefault()
    }

    @AfterTest
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `English reads the rate in decimal kilobytes per second`() = runTest {
        Locale.setDefault(Locale.US)

        assertEquals("50% (12.60 kB/s, ETA: 79s)", sample().resolve())
    }

    @Test
    fun `a comma-decimal locale gets its own separator in the rate`() = runTest {
        Locale.setDefault(Locale.GERMANY)
        val text = sample().resolve()

        assertTrue("12,60 kB" in text, text)
    }

    @Test
    fun `before a throughput sample only the percentage shows`() = runTest {
        Locale.setDefault(Locale.US)

        assertEquals("50%", formatTransferProgress(progress = 0.5f, totalBytes = 1000, bytesPerSecond = 0).resolve())
    }

    @Test
    fun `download progress reads as the bare percentage`() = runTest {
        Locale.setDefault(Locale.US)

        assertEquals("25%", formatTransferPercent(progress = 0.25f).resolve())
    }

    private fun sample() = formatTransferProgress(progress = 0.5f, totalBytes = 2_000_000, bytesPerSecond = 12_600)
}
