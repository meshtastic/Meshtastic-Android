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
package org.meshtastic.core.service

import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceStayAliveDecisionTest {

    private val anyAddressConnects: (String) -> Boolean = { true }

    private val noAddressConnects: (String) -> Boolean = { false }

    @Test
    fun selectedDeviceKeepsServiceAlive() {
        assertEquals(
            ServiceStayAliveDecision.STAY_FOR_DEVICE,
            serviceStayAliveDecision(
                address = "x11:22:33:44:55:66",
                hasActiveRadioOperation = false,
                canConnect = anyAddressConnects,
            ),
        )
    }

    @Test
    fun selectedDeviceWinsOverOperation() {
        assertEquals(
            ServiceStayAliveDecision.STAY_FOR_DEVICE,
            serviceStayAliveDecision(
                address = "t10.0.2.2:4403",
                hasActiveRadioOperation = true,
                canConnect = anyAddressConnects,
            ),
        )
    }

    @Test
    fun deselectedDuringAnOperationStaysAlive() {
        // The exact firmware-update case: the flow sets the address to the "none" sentinel to free the transport.
        assertEquals(
            ServiceStayAliveDecision.STAY_FOR_OPERATION,
            serviceStayAliveDecision(address = "n", hasActiveRadioOperation = true, canConnect = anyAddressConnects),
        )
    }

    @Test
    fun noAddressDuringAnOperationStaysAlive() {
        assertEquals(
            ServiceStayAliveDecision.STAY_FOR_OPERATION,
            serviceStayAliveDecision(address = null, hasActiveRadioOperation = true, canConnect = anyAddressConnects),
        )
    }

    @Test
    fun deselectedWithNothingRunningStops() {
        assertEquals(
            ServiceStayAliveDecision.STOP,
            serviceStayAliveDecision(address = "n", hasActiveRadioOperation = false, canConnect = anyAddressConnects),
        )
    }

    @Test
    fun blankAddressWithNothingRunningStops() {
        assertEquals(
            ServiceStayAliveDecision.STOP,
            serviceStayAliveDecision(address = "", hasActiveRadioOperation = false, canConnect = anyAddressConnects),
        )
        assertEquals(
            ServiceStayAliveDecision.STOP,
            serviceStayAliveDecision(address = null, hasActiveRadioOperation = false, canConnect = anyAddressConnects),
        )
    }

    @Test
    fun savedAddressThatCannotConnectStops() {
        assertEquals(
            ServiceStayAliveDecision.STOP,
            serviceStayAliveDecision(
                address = "s1027:29987:0",
                hasActiveRadioOperation = false,
                canConnect = noAddressConnects,
            ),
        )
    }

    @Test
    fun savedAddressThatCannotConnectStillStaysForAnOperation() {
        assertEquals(
            ServiceStayAliveDecision.STAY_FOR_OPERATION,
            serviceStayAliveDecision(
                address = "s1027:29987:0",
                hasActiveRadioOperation = true,
                canConnect = noAddressConnects,
            ),
        )
    }
}
