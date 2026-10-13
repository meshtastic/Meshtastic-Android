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
package org.meshtastic.feature.map.maplibre

import co.touchlab.kermit.Severity
import org.maplibre.compose.logging.MapLogLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class MapLibreLoggingTest {
    @Test
    fun everyLevelMapsToTheMatchingSeverity() {
        assertEquals(Severity.Debug, MapLogLevel.Debug.toSeverity())
        assertEquals(Severity.Info, MapLogLevel.Info.toSeverity())
        assertEquals(Severity.Warn, MapLogLevel.Warning.toSeverity())
        assertEquals(Severity.Error, MapLogLevel.Error.toSeverity())
    }

    @Test
    fun categoryPrefixesTheMessageOnlyWhenPresent() {
        assertEquals("[bridge] blank", mapLogMessage("bridge", "blank"))
        assertEquals("blank", mapLogMessage(null, "blank"))
    }
}
