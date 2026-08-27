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
package org.meshtastic.desktop

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The language preference list holds language-only tags. Taking one verbatim used to drop the region, and the region is
 * what decides the measurement system — so choosing English switched a user in a metric country to feet.
 */
class ResolveLocaleTest {

    @Test
    fun `a language-only preference keeps the system region`() {
        assertEquals(Locale.forLanguageTag("en-ID"), resolveLocale("en", Locale.forLanguageTag("id-ID")))
        assertEquals(Locale.forLanguageTag("en-FR"), resolveLocale("en", Locale.forLanguageTag("fr-FR")))
    }

    @Test
    fun `a preference naming its own region wins outright`() {
        assertEquals(Locale.forLanguageTag("pt-BR"), resolveLocale("pt-BR", Locale.forLanguageTag("id-ID")))
    }

    @Test
    fun `no preference leaves the system locale untouched`() {
        val system = Locale.forLanguageTag("fr-FR")
        assertEquals(system, resolveLocale("", system))
    }

    @Test
    fun `a region-less system locale cannot supply one`() {
        assertEquals(Locale.forLanguageTag("en"), resolveLocale("en", Locale.forLanguageTag("fr")))
    }
}
