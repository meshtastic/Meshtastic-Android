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
package org.meshtastic.core.network.repository
package org.meshtastic.core.network.repository

import app.cash.turbine.test
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.transport.tcp.DiscoveredTcpRadio
import org.meshtastic.sdk.transport.tcp.TcpDiscoveryEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class TcpServiceListTest {

    private fun radio(name: String, address: String = "10.0.0.1") =
        DiscoveredTcpRadio(serviceName = name, addresses = listOf(address), port = 4403, attributes = emptyMap())

    // Regression: Lost ignored, so a powered-off node stays selectable forever.
    @Test
    fun `a resolved radio is dropped from the list when its service is lost`() = runTest {
        flowOf(
            TcpDiscoveryEvent.Found(radio("a")),
            TcpDiscoveryEvent.Found(radio("b")),
            TcpDiscoveryEvent.Lost("a"),
        )
            .toServiceList()
            .test {
                assertEquals(emptyList(), awaitItem())
                assertEquals(listOf("a"), awaitItem().map { it.name })
                assertEquals(listOf("a", "b"), awaitItem().map { it.name })
                assertEquals(listOf("b"), awaitItem().map { it.name })
                awaitComplete()
            }
    }

    // Regression: keying by address gives duplicate rows and a LazyColumn duplicate-key crash.
    @Test
    fun `a radio found again under the same service name replaces its entry`() = runTest {
        flowOf(
            TcpDiscoveryEvent.Found(radio("Meshtastic", "10.0.0.5")),
            TcpDiscoveryEvent.Found(radio("Meshtastic", "10.0.0.9")),
        )
            .toServiceList()
            .test {
                awaitItem()
                awaitItem()
                val last = awaitItem()
                assertEquals(1, last.size)
                assertEquals("10.0.0.9", last.single().hostAddress)
                awaitComplete()
            }
    }

    // Regression: no seed value holds back the combine in CommonGetDiscoveredDevicesUseCase.
    @Test
    fun `the list starts empty before any radio is found`() = runTest {
        emptyFlow<TcpDiscoveryEvent>().toServiceList().test {
            assertEquals(emptyList(), awaitItem())
            awaitComplete()
        }
    }

    // Regression: an uncaught Transport failure escapes into NetworkRepositoryImpl's shareIn scope.
    @Test
    fun `a discovery that fails to start ends the list without throwing`() = runTest {
        flow<TcpDiscoveryEvent> { throw MeshtasticException.Transport("no multicast") }
            .toServiceList()
            .test {
                assertEquals(emptyList(), awaitItem())
                awaitComplete()
            }
    }

    // Regression: the adapter drops or renames a TXT key, renaming every discovered node to the default.
    @Test
    fun `a radio is shown with the host port and TXT keys the connections list reads`() = runTest {
        val found =
            DiscoveredTcpRadio(
                serviceName = "Meshtastic",
                addresses = listOf("192.168.1.50", "fd00::1"),
                port = 4403,
                attributes = mapOf("shortname" to "ptzm", "id" to "!a1b2c3d4"),
            )
        flowOf(TcpDiscoveryEvent.Found(found)).toServiceList().test {
            awaitItem()
            val service = awaitItem().single()
            assertEquals("192.168.1.50", service.hostAddress)
            assertEquals(4403, service.port)
            assertEquals("ptzm", service.txt["shortname"]!!.decodeToString())
            assertEquals("!a1b2c3d4", service.txt["id"]!!.decodeToString())
            awaitComplete()
        }
    }
}
