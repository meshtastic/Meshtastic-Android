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
package org.meshtastic.core.common.util

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locale-dependent formatting, pinned per test rather than relying on the build's en-US default, so these assertions
 * mean something on any machine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeasureFormattingTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `German locale formats the decimal separator as a comma`() {
        Locale.setDefault(Locale.GERMANY)

        val formatted = formatMeasure(1.6, MeasureUnitKind.KILOMETER, fractionDigits = 1)

        assertTrue(formatted.contains("1,6"), "expected a comma separator in \"$formatted\"")
        assertTrue(formatted.contains("km"), "expected the unit symbol in \"$formatted\"")
    }

    @Test
    fun `US locale formats the decimal separator as a dot`() {
        Locale.setDefault(Locale.US)

        val formatted = formatMeasure(1.6, MeasureUnitKind.KILOMETER, fractionDigits = 1)

        assertTrue(formatted.contains("1.6"), "expected a dot separator in \"$formatted\"")
        assertTrue(formatted.contains("km"), "expected the unit symbol in \"$formatted\"")
    }

    @Test
    fun `whole units carry no decimal separator`() {
        Locale.setDefault(Locale.US)

        val formatted = formatMeasure(850.0, MeasureUnitKind.METER, fractionDigits = 0)

        assertTrue(formatted.contains("850"), "expected the whole value in \"$formatted\"")
        assertTrue(!formatted.contains("850."), "expected no fractional part in \"$formatted\"")
    }

    /**
     * The number is localized; the symbol is not. Translating symbols would mean ICU's `MeasureFormat`, which is
     * unreachable from `commonTest` under the stub `android.jar` — see [formatMeasure]'s KDoc.
     */
    @Test
    fun `the number is localized but the unit symbol is fixed`() {
        Locale.setDefault(Locale.GERMANY)

        val formatted = formatMeasure(2.5, MeasureUnitKind.KILOGRAM, fractionDigits = 1)

        assertTrue(formatted.contains("2,5"), "expected a localized number in \"$formatted\"")
        assertTrue(formatted.contains("kg"), "expected the fixed symbol in \"$formatted\"")
    }

    /** Thousands grouping is part of locale formatting, and differs between these two locales. */
    @Test
    fun `large values are grouped per locale`() {
        Locale.setDefault(Locale.US)
        assertEquals("1,013.3", NumberFormatter.format(1013.25, 1))

        Locale.setDefault(Locale.GERMANY)
        assertEquals("1.013,3", NumberFormatter.format(1013.25, 1))
    }

    /** Plain numbers follow the locale too, so a telemetry voltage reads the way its reader writes numbers. */
    @Test
    fun `plain decimals follow the locale`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("3,85", NumberFormatter.format(3.85, 2))

        Locale.setDefault(Locale.US)
        assertEquals("3.85", NumberFormatter.format(3.85, 2))
    }

    /** The invariant path is what CoT payloads use; the locale must never reach it. */
    @Test
    fun `the invariant formatter ignores the locale`() {
        Locale.setDefault(Locale.GERMANY)

        assertEquals("3.85", NumberFormatter.formatInvariant(3.85, 2))
    }

    /**
     * Half away from zero on both paths. -6.25 is a binary-exact tie, unlike 1.005 (stored as 1.00499999999999989), so
     * it actually discriminates the rounding modes — and firmware reports SNR in quarter-dB steps, making negative ties
     * routine.
     */
    @Test
    fun `both paths round negative ties the same way`() {
        Locale.setDefault(Locale.US)

        assertEquals("-6.3", NumberFormatter.format(-6.25, 1))
        assertEquals("-6.3", NumberFormatter.formatInvariant(-6.25, 1))
    }

    @Test
    fun `every unit kind formats without throwing`() {
        Locale.setDefault(Locale.US)

        val formatted = MeasureUnitKind.entries.map { formatMeasure(1.0, it, fractionDigits = 1) }

        assertEquals(MeasureUnitKind.entries.size, formatted.count { it.isNotBlank() })
    }
}
