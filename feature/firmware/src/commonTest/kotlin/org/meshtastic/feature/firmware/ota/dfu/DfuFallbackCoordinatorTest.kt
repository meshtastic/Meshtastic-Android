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
        val budgets = mutableListOf<DfuAttemptBudget>()
        assertFailsWith<RuntimeException> {
            coordinator.execute { protocol, budget ->
                protocols.add(protocol)
                budgets.add(budget)
                if (protocol == DfuProtocolKind.LEGACY) {
                    DfuUploadResult.Failure(RuntimeException("connect failed"), protocolEngaged = false)
                } else {
                    DfuUploadResult.Failure(RuntimeException("also failed"), protocolEngaged = false)
                }
            }
        }
            .also { thrown ->
                assertEquals("connect failed", thrown.message)
                assertEquals(1, thrown.suppressedExceptions.size, "alternate error should be suppressed on primary")
                assertEquals("also failed", thrown.suppressedExceptions.first().message)
            }
        assertEquals(listOf(DfuProtocolKind.LEGACY, DfuProtocolKind.SECURE), protocols)
        // Unknown Legacy primary budget shape: pre=1 probe, engaged=3; promotion is not exercised because this test
        // fails before engagement. Secure remains 1/1.
        assertEquals(listOf(DfuAttemptBudget(1, 3), DfuAttemptBudget(1, 1)), budgets)
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
    fun `LegacyObserved gives Legacy 3 upload attempts`() = runTest {
        val coordinator = DfuFallbackCoordinator(BootloaderDetection.LegacyObserved)
        val budgets = mutableListOf<DfuAttemptBudget>()
        coordinator.execute { _, budget ->
            budgets.add(budget)
            DfuUploadResult.Success
        }
        assertEquals(
            listOf(DfuAttemptBudget(3, 3)),
            budgets,
        ) // LEGACY_SESSION_ATTEMPTS — upload-attempt budget; stale cleanup is separate
    }

    @Test
    fun `LegacyObserved skips Secure fallback when Legacy never engages`() = runTest {
        val coordinator = DfuFallbackCoordinator(BootloaderDetection.LegacyObserved)
        val protocols = mutableListOf<DfuProtocolKind>()
        assertFailsWith<RuntimeException> {
            coordinator.execute { protocol, _ ->
                protocols.add(protocol)
                DfuUploadResult.Failure(RuntimeException("legacy connect failed"), protocolEngaged = false)
            }
        }
            .also { thrown -> assertEquals("legacy connect failed", thrown.message) }
        assertEquals(listOf(DfuProtocolKind.LEGACY), protocols)
    }

    @Test
    fun `LegacyObserved gives Secure fallback budget of 1 upload attempt after Legacy engages`() = runTest {
        val coordinator = DfuFallbackCoordinator(BootloaderDetection.LegacyObserved)
        val budgets = mutableListOf<DfuAttemptBudget>()
        coordinator.execute { protocol, budget ->
            budgets.add(budget)
            if (protocol == DfuProtocolKind.LEGACY) {
                DfuUploadResult.Failure(RuntimeException("legacy protocol failed"), protocolEngaged = true)
            } else {
                DfuUploadResult.Success
            }
        }
        assertEquals(
            listOf(DfuAttemptBudget(3, 3), DfuAttemptBudget(1, 1)),
            budgets,
        ) // Legacy primary=LEGACY_SESSION_ATTEMPTS (upload attempts), Secure fallback=LIMITED_SESSION_ATTEMPTS
    }

    @Test
    fun `Unknown gives Legacy primary a pre-engagement probe budget of 1 with engaged promotion to 3`() = runTest {
        val coordinator = DfuFallbackCoordinator(BootloaderDetection.Unknown)
        val budgets = mutableListOf<DfuAttemptBudget>()
        coordinator.execute { _, budget ->
            budgets.add(budget)
            DfuUploadResult.Success
        }
        assertEquals(
            listOf(DfuAttemptBudget(1, 3)),
            budgets,
        ) // LIMITED_SESSION_ATTEMPTS keeps speculative Unknown probes bounded; engagement promotes to 3
    }

    @Test
    fun `SecureObserved skips Legacy fallback when Secure never engages`() = runTest {
        val coordinator = DfuFallbackCoordinator(BootloaderDetection.SecureObserved)
        val protocols = mutableListOf<DfuProtocolKind>()
        assertFailsWith<RuntimeException> {
            coordinator.execute { protocol, _ ->
                protocols.add(protocol)
                DfuUploadResult.Failure(RuntimeException("secure connect failed"), protocolEngaged = false)
            }
        }
            .also { thrown -> assertEquals("secure connect failed", thrown.message) }
        assertEquals(listOf(DfuProtocolKind.SECURE), protocols)
    }

    @Test
    fun `SecureObserved throws Secure error with Legacy suppressed when both fail`() = runTest {
        val coordinator = DfuFallbackCoordinator(BootloaderDetection.SecureObserved)
        val protocols = mutableListOf<DfuProtocolKind>()
        assertFailsWith<RuntimeException> {
            coordinator.execute { protocol, _ ->
                protocols.add(protocol)
                if (protocol == DfuProtocolKind.SECURE) {
                    DfuUploadResult.Failure(RuntimeException("secure protocol failed"), protocolEngaged = true)
                } else {
                    DfuUploadResult.Failure(RuntimeException("legacy also failed"), protocolEngaged = false)
                }
            }
        }
            .also { thrown ->
                assertEquals("secure protocol failed", thrown.message)
                assertEquals(1, thrown.suppressedExceptions.size, "alternate error should be suppressed on primary")
                assertEquals("legacy also failed", thrown.suppressedExceptions.first().message)
            }
        assertEquals(listOf(DfuProtocolKind.SECURE, DfuProtocolKind.LEGACY), protocols)
    }
}
