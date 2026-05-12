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
package org.meshtastic.feature.docs

import org.meshtastic.core.navigation.SettingsRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class DocsNavigationTest {

    @Test
    fun `HelpDocs route serializable`() {
        val route = SettingsRoute.HelpDocs
        assertEquals(SettingsRoute.HelpDocs, route)
    }

    @Test
    fun `HelpDocPage route preserves page id`() {
        val route = SettingsRoute.HelpDocPage("messages-and-channels")
        assertEquals("messages-and-channels", route.pageId)
    }

    @Test
    fun `HelpDocPage route equality`() {
        val route1 = SettingsRoute.HelpDocPage("onboarding")
        val route2 = SettingsRoute.HelpDocPage("onboarding")
        assertEquals(route1, route2)
    }
}
