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

import android.content.res.Configuration
import android.text.format.Formatter
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The ICU engine renders what the platform's own `Formatter.formatFileSize` renders for the same locale, bidi wrap
 * included. Below 901 bytes Formatter prints the framework's translated byte symbol, which an app cannot read, so
 * non-English locales are compared from 901 up. Pinned strings are CLDR output from probe; a change on an ICU update is
 * a rendering change to review, not automatically a bug.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ByteSizeFormattingAndroidTest {

    private val original: Locale = Locale.getDefault()

    @After
    fun tearDown() {
        Locale.setDefault(original)
    }

    @Test
    fun `English matches the platform formatter at every threshold`() {
        assertMatchesPlatform(Locale.US, BYTE_SIZES + SIZES)
    }

    @Test
    fun `French matches the platform formatter`() {
        assertMatchesPlatform(Locale.FRANCE, SIZES)
    }

    @Test
    fun `German matches the platform formatter`() {
        assertMatchesPlatform(Locale.GERMANY, SIZES)
    }

    @Test
    fun `Russian matches the platform formatter`() {
        assertMatchesPlatform(Locale.forLanguageTag("ru-RU"), SIZES)
    }

    @Test
    fun `Arabic matches the platform formatter`() {
        assertMatchesPlatform(Locale.forLanguageTag("ar"), SIZES)
    }

    @Test
    fun `Hebrew matches the platform formatter`() {
        assertMatchesPlatform(Locale.forLanguageTag("he"), SIZES)
    }

    @Test
    fun `French unit labels are CLDR's`() {
        Locale.setDefault(Locale.FRANCE)
        assertEquals("1,50 ko", formatByteSize(1_500L))
        assertEquals("17 180 Mo", formatMegabytes(17_179.869184, 0))
    }

    @Test
    fun `a right-to-left locale wraps the value in bidi controls`() {
        Locale.setDefault(Locale.forLanguageTag("he"))
        val formatted = formatByteSize(1_500L)

        assertNotEquals("1.50 kB", formatted)
        assertEquals("1.50 kB", formatted.filterNot { it in BIDI_CONTROLS })
    }

    private fun assertMatchesPlatform(locale: Locale, sizes: List<Long>) {
        Locale.setDefault(locale)
        val app = RuntimeEnvironment.getApplication()
        val config = Configuration(app.resources.configuration).apply { setLocale(locale) }
        val context = app.createConfigurationContext(config)
        sizes.forEach { bytes ->
            assertEquals(Formatter.formatFileSize(context, bytes), formatByteSize(bytes), "$bytes bytes in $locale")
        }
    }

    private companion object {
        val BYTE_SIZES = listOf(0L, 1L, 900L)
        val SIZES =
            listOf(
                901L,
                1_024L,
                1_500L,
                99_500L,
                100_000L,
                900_000L,
                900_001L,
                1_500_000L,
                17_179_869_184L,
                250_000_000_000L,
                1_000_000_000_000L,
            )
        val BIDI_CONTROLS = setOf('‎', '‏', '‪', '‫', '‬', '⁦', '⁧', '⁨', '⁩')
    }
}
