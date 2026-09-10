/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Meshtastic Android-derived portions, where present:
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
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
package com.ntsocial.meshlink.core.ble

import com.ntsocial.meshlink.core.di.CoroutineDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class JvmDesktopBluetoothPairingServiceTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher)

    @Test
    fun `non-Windows platform preserves direct connect behavior`() = runTest(dispatcher) {
        val runner = FakePairingProcessRunner(successResult("Paired"))
        val service = pairingService(runner = runner, isWindows = false)

        assertFalse(service.isExplicitPairingRequired)
        assertEquals(DesktopBluetoothPairingOutcome.NOT_REQUIRED, service.ensurePaired("AA:BB:CC:DD:EE:FF"))
        assertEquals(0, runner.callCount)
    }

    @Test
    fun `Windows pairing normalizes address and invokes PairAsync`() = runTest(dispatcher) {
        val runner = FakePairingProcessRunner(successResult("Paired"))
        val service = pairingService(runner = runner)

        assertEquals(DesktopBluetoothPairingOutcome.PAIRED, service.ensurePaired("aa:bb:cc:dd:ee:ff"))

        val script =
            String(Base64.getDecoder().decode(requireNotNull(runner.lastEncodedCommand)), StandardCharsets.UTF_16LE)
        assertTrue(script.contains("ToUInt64('AABBCCDDEEFF', 16)"))
        assertTrue(script.contains("PairAsync()"))
        assertTrue(script.contains("PAIRING_STATUS="))
    }

    @Test
    fun `already paired result continues without a new bond`() = runTest(dispatcher) {
        val service = pairingService(FakePairingProcessRunner(successResult("AlreadyPaired")))

        assertEquals(DesktopBluetoothPairingOutcome.ALREADY_PAIRED, service.ensurePaired("AA-BB-CC-DD-EE-FF"))
    }

    @Test
    fun `pairing cancellation is user actionable`() = runTest(dispatcher) {
        val service = pairingService(FakePairingProcessRunner(successResult("PairingCanceled")))

        val error = assertFailsWith<BlePairingException> { service.ensurePaired("AA:BB:CC:DD:EE:FF") }
        assertEquals(BlePairingFailure.CANCELED, error.failure)
        assertTrue(error.classifyBleException()?.isPermanent == true)
    }

    @Test
    fun `pairing process timeout is reported without entering GATT`() = runTest(dispatcher) {
        val service =
            pairingService(
                FakePairingProcessRunner(WindowsPairingProcessResult(exitCode = null, output = "", timedOut = true)),
            )

        val error = assertFailsWith<BlePairingException> { service.ensurePaired("AA:BB:CC:DD:EE:FF") }
        assertEquals(BlePairingFailure.TIMED_OUT, error.failure)
    }

    @Test
    fun `invalid address is rejected before starting helper process`() = runTest(dispatcher) {
        val runner = FakePairingProcessRunner(successResult("Paired"))
        val service = pairingService(runner)

        val error = assertFailsWith<BlePairingException> { service.ensurePaired("not-an-address") }
        assertEquals(BlePairingFailure.PLATFORM_FAILURE, error.failure)
        assertEquals(0, runner.callCount)
    }

    @Test
    fun `repository requires explicit pairing only on Windows`() = runTest(dispatcher) {
        val windowsService = pairingService(FakePairingProcessRunner(successResult("Paired")))
        val windowsRepository = KableBluetoothRepository(windowsService)
        val device = MeshtasticBleDevice(address = "AA:BB:CC:DD:EE:FF", name = "Meshtastic_test")

        assertFalse(windowsRepository.isBonded(device.address))
        windowsRepository.bond(device)

        val otherService = pairingService(FakePairingProcessRunner(successResult("Paired")), isWindows = false)
        val otherRepository = KableBluetoothRepository(otherService)
        assertTrue(otherRepository.isBonded(device.address))
    }

    @Test
    fun `every helper exception keeps pairing fail closed`() = runTest(dispatcher) {
        listOf(IOException("launch"), SecurityException("denied"), IllegalStateException("helper defect"))
            .forEach { failure ->
                val service = pairingService(failingRunner(failure))
                val error = assertFailsWith<BlePairingException> { service.ensurePaired("AA:BB:CC:DD:EE:FF") }
                assertEquals(BlePairingFailure.PLATFORM_FAILURE, error.failure)
                assertCauseRetained(error, failure)
                assertTrue(error.classifyBleException()?.isPermanent == true)
            }
    }

    @Test
    fun `coroutine cancellation is propagated without reclassification`() = runTest(dispatcher) {
        val cancellation = CancellationException("cancel helper")
        val service = pairingService(failingRunner(cancellation))
        val error = assertFailsWith<CancellationException> { service.ensurePaired("AA:BB:CC:DD:EE:FF") }
        assertCauseRetained(error, cancellation)
    }

    @Test
    fun `fatal helper errors are not converted to user pairing failures`() = runTest(dispatcher) {
        val failure = AssertionError("fatal helper error")
        val service = pairingService(failingRunner(failure))
        val error = assertFailsWith<AssertionError> { service.ensurePaired("AA:BB:CC:DD:EE:FF") }
        assertCauseRetained(error, failure)
    }

    private fun assertCauseRetained(actual: Throwable, expected: Throwable) {
        // Coroutine stack-trace recovery may copy exceptions while retaining the original as their cause.
        assertTrue(generateSequence(actual) { it.cause }.any { it === expected })
    }

    private fun failingRunner(failure: Throwable) = object : WindowsPairingProcessRunner {
        override fun run(encodedCommand: String, timeoutMillis: Long): WindowsPairingProcessResult = throw failure
    }

    private fun pairingService(
        runner: WindowsPairingProcessRunner,
        isWindows: Boolean = true,
    ): JvmDesktopBluetoothPairingService = JvmDesktopBluetoothPairingService(
        dispatchers = dispatchers,
        processRunner = runner,
        platform =
        object : DesktopBluetoothPlatform {
            override val isWindows: Boolean = isWindows
        },
    )

    private fun successResult(status: String) =
        WindowsPairingProcessResult(exitCode = 0, output = "PAIRING_STATUS=$status", timedOut = false)

    private class FakePairingProcessRunner(private val result: WindowsPairingProcessResult) :
        WindowsPairingProcessRunner {
        var callCount: Int = 0
            private set

        var lastEncodedCommand: String? = null
            private set

        override fun run(encodedCommand: String, timeoutMillis: Long): WindowsPairingProcessResult {
            callCount++
            lastEncodedCommand = encodedCommand
            return result
        }
    }
}
