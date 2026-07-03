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
@file:OptIn(ExperimentalCoroutinesApi::class)

package org.meshtastic.feature.firmware.ota.dfu

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DfuFallbackCoordinatorTest {

    @Test
    fun `LegacyObserved tries Legacy first`() = runTest {
        val coordinator = DfuFallbackCoordinator(BootloaderDetection.LegacyObserved)
        val protocols = mutableListOf<DfuProtocolKind>()
        coordinator.execute { protocol, _ ->
            protocols.add(protocol)
            DfuUploadResult.Success
        }
        assertEquals(listOf(DfuProtocolKind.LEGACY), protocols)
    }

    @Test
    fun `SecureObserved tries Secure first`() = runTest {
        val coordinator = DfuFallbackCoordinator(BootloaderDetection.SecureObserved)
        val protocols = mutableListOf<DfuProtocolKind>()
        coordinator.execute { protocol, _ ->
            protocols.add(protocol)
            DfuUploadResult.Success
        }
        assertEquals(listOf(DfuProtocolKind.SECURE), protocols)
    }

    @Test
    fun `Unknown tries Legacy first`() = runTest {
        val coordinator = DfuFallbackCoordinator(BootloaderDetection.Unknown)
        val protocols = mutableListOf<DfuProtocolKind>()
        coordinator.execute { protocol, _ ->
            protocols.add(protocol)
            DfuUploadResult.Success
        }
        assertEquals(listOf(DfuProtocolKind.LEGACY), protocols) // Success on first = no fallback
    }

    @Test
    fun `Unknown falls back from Legacy to Secure on pre-engagement failure`() = runTest {
        val coordinator = DfuFallbackCoordinator(BootloaderDetection.Unknown)
        val protocols = mutableListOf<DfuProtocolKind>()
        assertFailsWith<RuntimeException> {
            coordinator.execute { protocol, _ ->
                protocols.add(protocol)
                if (protocol == DfuProtocolKind.LEGACY) {
                    DfuUploadResult.Failure(RuntimeException("connect failed"), protocolEngaged = false)
                } else {
                    DfuUploadResult.Failure(RuntimeException("also failed"), protocolEngaged = false)
                }
            }
        }
            .also { thrown ->
                assertEquals(1, thrown.suppressedExceptions.size, "prior error should be suppressed on final throw")
                assertEquals("connect failed", thrown.suppressedExceptions.first().message)
            }
        assertEquals(listOf(DfuProtocolKind.LEGACY, DfuProtocolKind.SECURE), protocols)
    }

    @Test
    fun `protocolEngaged prevents fallback`() = runTest {
        val coordinator = DfuFallbackCoordinator(BootloaderDetection.Unknown)
        val protocols = mutableListOf<DfuProtocolKind>()
        assertFailsWith<RuntimeException> {
            coordinator.execute { protocol, _ ->
                protocols.add(protocol)
                DfuUploadResult.Failure(RuntimeException("mid-transfer"), protocolEngaged = true)
            }
        }
        assertEquals(listOf(DfuProtocolKind.LEGACY), protocols) // No fallback despite Unknown
    }

    @Test
    fun `LegacyObserved gives Legacy 3 session attempts`() = runTest {
        val coordinator = DfuFallbackCoordinator(BootloaderDetection.LegacyObserved)
        val attemptCounts = mutableListOf<Int>()
        coordinator.execute { _, attempts ->
            attemptCounts.add(attempts)
            DfuUploadResult.Success
        }
        assertEquals(listOf(3), attemptCounts) // LEGACY_SESSION_ATTEMPTS
    }

    @Test
    fun `LegacyObserved gives Secure fallback 1 session attempt`() = runTest {
        val coordinator = DfuFallbackCoordinator(BootloaderDetection.LegacyObserved)
        val attemptCounts = mutableListOf<Int>()
        coordinator.execute { protocol, attempts ->
            attemptCounts.add(attempts)
            if (protocol == DfuProtocolKind.LEGACY) {
                DfuUploadResult.Failure(RuntimeException("legacy connect failed"), protocolEngaged = false)
            } else {
                DfuUploadResult.Success
            }
        }
        assertEquals(
            listOf(3, 1),
            attemptCounts,
        ) // Legacy primary=LEGACY_SESSION_ATTEMPTS, Secure fallback=LIMITED_SESSION_ATTEMPTS
    }

    @Test
    fun `Unknown gives Legacy primary 3 session attempts`() = runTest {
        val coordinator = DfuFallbackCoordinator(BootloaderDetection.Unknown)
        val attemptCounts = mutableListOf<Int>()
        coordinator.execute { _, attempts ->
            attemptCounts.add(attempts)
            DfuUploadResult.Success
        }
        assertEquals(listOf(3), attemptCounts) // LEGACY_SESSION_ATTEMPTS — Unknown→Legacy needs reset-prime budget too
    }

    @Test
    fun `SecureObserved falls back from Secure to Legacy on pre-engagement failure`() = runTest {
        val coordinator = DfuFallbackCoordinator(BootloaderDetection.SecureObserved)
        val protocols = mutableListOf<DfuProtocolKind>()
        assertFailsWith<RuntimeException> {
            coordinator.execute { protocol, _ ->
                protocols.add(protocol)
                if (protocol == DfuProtocolKind.SECURE) {
                    DfuUploadResult.Failure(RuntimeException("secure connect failed"), protocolEngaged = false)
                } else {
                    DfuUploadResult.Failure(RuntimeException("legacy also failed"), protocolEngaged = false)
                }
            }
        }
            .also { thrown ->
                assertEquals(1, thrown.suppressedExceptions.size, "prior error should be suppressed on final throw")
                assertEquals("secure connect failed", thrown.suppressedExceptions.first().message)
            }
        assertEquals(listOf(DfuProtocolKind.SECURE, DfuProtocolKind.LEGACY), protocols)
    }
}
