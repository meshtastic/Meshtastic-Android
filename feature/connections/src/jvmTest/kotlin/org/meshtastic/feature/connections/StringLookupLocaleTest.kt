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
package org.meshtastic.feature.connections

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.getStringSuspend
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** The lookups reuse one resource environment, which must still follow an in-app language switch. */
class StringLookupLocaleTest {

    @Test
    fun `a language switch reaches the next lookup`() = runTest {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMAN)
            assertEquals("Abbrechen", getStringSuspend(Res.string.cancel))
            assertEquals("Abbrechen", getStringSuspend(Res.string.cancel))

            Locale.setDefault(Locale.FRENCH)
            assertEquals("Annuler", getStringSuspend(Res.string.cancel))
            assertEquals("Annuler", getString(Res.string.cancel))

            Locale.setDefault(Locale.ENGLISH)
            assertEquals("Cancel", getStringSuspend(Res.string.cancel))
        } finally {
            Locale.setDefault(original)
        }
    }
}
