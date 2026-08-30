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

import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals

class RoleLabelTest {

    @Test
    fun `no two roles share a label`() {
        // A thirteen-branch `when` of near-identical lines is exactly where a copy-paste puts ROUTER's label on
        // ROUTER_LATE, and the compiler cannot see it. Exhaustiveness is enforced by the absence of an `else`.
        @Suppress("DEPRECATION")
        val roles = Config.DeviceConfig.Role.entries
        assertEquals(roles.size, roles.map { it.label.key }.toSet().size)
    }
}
