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
package org.meshtastic.feature.intro

import org.meshtastic.core.ui.util.PermissionStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class IntroPermissionActionTest {

    @Test
    fun `granted advances`() {
        assertEquals(IntroPermissionAction.ADVANCE, introPermissionAction(PermissionStatus.GRANTED))
    }

    @Test
    fun `never asked requests`() {
        assertEquals(IntroPermissionAction.REQUEST, introPermissionAction(PermissionStatus.NOT_REQUESTED))
    }

    @Test
    fun `a single denial still requests because the system will still prompt`() {
        assertEquals(IntroPermissionAction.REQUEST, introPermissionAction(PermissionStatus.DENIED_CAN_RETRY))
    }

    /**
     * The regression this whole mapping exists for: requesting a permanently denied permission returns an immediate
     * denial with no dialog, so the primary button must route to app settings instead of looking broken.
     */
    @Test
    fun `permanent denial routes to app settings rather than a no-op request`() {
        assertEquals(IntroPermissionAction.OPEN_SETTINGS, introPermissionAction(PermissionStatus.PERMANENTLY_DENIED))
    }

    @Test
    fun `every status maps to an action`() {
        PermissionStatus.entries.forEach { status -> introPermissionAction(status) }
    }
}
