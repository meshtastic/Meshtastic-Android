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

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.window.core.layout.WindowSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationSuiteLayoutTypeTest {

    private fun layoutFor(widthDp: Int, heightDp: Int, tabletop: Boolean = false) = navigationSuiteLayoutType(
        WindowAdaptiveInfo(WindowSizeClass(widthDp, heightDp), Posture(isTabletop = tabletop)),
    )

    @Test
    fun phoneInPortraitUsesBottomBar() {
        assertEquals(NavigationSuiteType.NavigationBar, layoutFor(widthDp = 411, heightDp = 891))
    }

    @Test
    fun phoneInLandscapeUsesRail() {
        assertEquals(NavigationSuiteType.NavigationRail, layoutFor(widthDp = 891, heightDp = 411))
    }

    @Test
    fun tabletUsesRail() {
        assertEquals(NavigationSuiteType.NavigationRail, layoutFor(widthDp = 1280, heightDp = 800))
    }

    @Test
    fun tabletopPostureUsesBottomBar() {
        assertEquals(NavigationSuiteType.NavigationBar, layoutFor(widthDp = 891, heightDp = 411, tabletop = true))
    }
}
