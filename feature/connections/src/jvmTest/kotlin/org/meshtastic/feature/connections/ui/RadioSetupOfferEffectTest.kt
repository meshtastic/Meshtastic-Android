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
package org.meshtastic.feature.connections.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoute
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RadioSetupOfferEffectTest {

    @Test
    fun opensTheRadioSetupForTheOfferedRadioOnce() = runComposeUiTest {
        val offered = mutableListOf<Int>()
        val routes = mutableListOf<Route>()
        setContent {
            RadioSetupOfferEffect(
                showWarnings = true,
                radioSetupOffer = 42,
                radioSetupAddress = "x00:11:22:33:44:55",
                onOfferRadioSetup = { offered += it },
                onConfigNavigate = { routes += it },
            )
        }

        runOnIdle {
            assertEquals(listOf(42), offered)
            assertEquals(listOf<Route>(SettingsRoute.RadioSetup("x00:11:22:33:44:55")), routes)
        }
    }

    @Test
    fun waitsForTheAddressAndForThePolicy() = runComposeUiTest {
        val routes = mutableListOf<Route>()
        setContent {
            RadioSetupOfferEffect(
                showWarnings = true,
                radioSetupOffer = 42,
                radioSetupAddress = null,
                onOfferRadioSetup = {},
                onConfigNavigate = { routes += it },
            )
            RadioSetupOfferEffect(
                showWarnings = false,
                radioSetupOffer = 42,
                radioSetupAddress = "x00:11:22:33:44:55",
                onOfferRadioSetup = {},
                onConfigNavigate = { routes += it },
            )
        }

        runOnIdle { assertEquals(emptyList(), routes) }
    }
}
