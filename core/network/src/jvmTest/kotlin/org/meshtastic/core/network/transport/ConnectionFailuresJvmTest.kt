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
package org.meshtastic.core.network.transport

import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionFailuresJvmTest {

    @Test
    fun `an unresolvable hostname is an expected connection failure`() {
        // Ktor throws this when the address will not resolve. It extends IllegalArgumentException, so before this
        // classification it escaped every catch(IOException) and was reported as an application error.
        assertTrue(UnresolvedAddressException().isExpectedConnectionFailure())
    }

    @Test
    fun `ordinary unreachable-host failures are expected connection failures`() {
        assertTrue(ConnectException("Connection refused").isExpectedConnectionFailure())
        assertTrue(UnknownHostException("no.such.host").isExpectedConnectionFailure())
        assertTrue(SocketException("Connection reset").isExpectedConnectionFailure())
        assertTrue(EOFException("Not enough data available").isExpectedConnectionFailure())
    }

    @Test
    fun `an unrelated IllegalArgumentException is still an error`() {
        // Guards the classification from widening to every IllegalArgumentException.
        assertFalse(IllegalArgumentException("bad port").isExpectedConnectionFailure())
    }
}
