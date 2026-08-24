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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckerTest {

    @Test
    fun `newer patch release is an update`() {
        assertTrue(UpdateChecker.isNewer(latest = "v2.8.1", current = "2.8.0"))
    }

    @Test
    fun `same version is not an update`() {
        assertFalse(UpdateChecker.isNewer(latest = "v2.8.0", current = "2.8.0"))
    }

    @Test
    fun `older release is not an update`() {
        assertFalse(UpdateChecker.isNewer(latest = "v2.7.14", current = "2.8.0"))
    }

    @Test
    fun `channel suffixes are ignored when comparing`() {
        assertFalse(UpdateChecker.isNewer(latest = "v2.8.0", current = "2.8.0-internal.24"))
        assertTrue(UpdateChecker.isNewer(latest = "v2.8.1", current = "2.8.0-internal.24"))
    }

    @Test
    fun `components compare numerically not lexically`() {
        assertTrue(UpdateChecker.isNewer(latest = "v2.10.0", current = "2.9.9"))
        assertTrue(UpdateChecker.isNewer(latest = "v3.0.0", current = "2.99.0"))
    }

    @Test
    fun `unparseable versions never trigger an update`() {
        assertFalse(UpdateChecker.isNewer(latest = "snapshot", current = "2.8.0"))
        assertFalse(UpdateChecker.isNewer(latest = "v2.8.1", current = "unknown"))
    }

    @Test
    fun `oversized version components never trigger an update`() {
        assertFalse(UpdateChecker.isNewer(latest = "v99999999999999999999.0.0", current = "2.8.0"))
    }
}
