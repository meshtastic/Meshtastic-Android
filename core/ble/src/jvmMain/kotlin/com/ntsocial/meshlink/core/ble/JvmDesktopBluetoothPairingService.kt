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
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Result of a successful Desktop pairing request. */
enum class DesktopBluetoothPairingOutcome {
    PAIRED,
    ALREADY_PAIRED,
    NOT_REQUIRED,
}

/** Platform pairing boundary used before Kable opens protected GATT characteristics. */
interface DesktopBluetoothPairingService {
    /** Whether this platform needs an explicit pairing operation before the Kable connection. */
    val isExplicitPairingRequired: Boolean

    /** Ensures the address is paired or throws [BlePairingException]. */
    suspend fun ensurePaired(address: String): DesktopBluetoothPairingOutcome
}

/** Runtime platform boundary kept injectable so pairing behavior is testable on every CI operating system. */
interface DesktopBluetoothPlatform {
    val isWindows: Boolean
}

@Single(binds = [DesktopBluetoothPlatform::class])
class SystemDesktopBluetoothPlatform : DesktopBluetoothPlatform {
    override val isWindows: Boolean =
        System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).startsWith(WINDOWS_OS_PREFIX)

    private companion object {
        const val WINDOWS_OS_PREFIX = "windows"
    }
}

/** Captured result of the constrained Windows pairing helper process. */
data class WindowsPairingProcessResult(val exitCode: Int?, val output: String, val timedOut: Boolean)

/** Process boundary separated for deterministic unit tests. */
interface WindowsPairingProcessRunner {
    fun run(encodedCommand: String, timeoutMillis: Long): WindowsPairingProcessResult
}

/**
 * Executes the built-in Windows PowerShell host without using PATH lookup.
 *
 * The command is UTF-16LE/Base64 encoded, accepts no user-authored script, and emits only a pairing status sentinel.
 * Windows owns the localized PIN UI and no PIN value is read by or returned to MeshLink.
 */
@Single(binds = [WindowsPairingProcessRunner::class])
class DefaultWindowsPairingProcessRunner : WindowsPairingProcessRunner {
    override fun run(encodedCommand: String, timeoutMillis: Long): WindowsPairingProcessResult {
        val systemRoot = System.getenv("SystemRoot")?.takeIf { it.isNotBlank() } ?: DEFAULT_WINDOWS_ROOT
        val powershell = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString()
        val process =
            ProcessBuilder(
                powershell,
                "-NoLogo",
                "-NoProfile",
                "-NonInteractive",
                "-WindowStyle",
                "Hidden",
                "-ExecutionPolicy",
                "Bypass",
                "-EncodedCommand",
                encodedCommand,
            )
                .redirectErrorStream(true)
                .start()

        return try {
            val completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroy()
                if (!process.waitFor(PROCESS_STOP_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                }
                WindowsPairingProcessResult(exitCode = null, output = "", timedOut = true)
            } else {
                val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                WindowsPairingProcessResult(exitCode = process.exitValue(), output = output, timedOut = false)
            }
        } catch (e: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            throw CancellationException("Windows Bluetooth pairing was interrupted", e)
        }
    }

    private companion object {
        const val DEFAULT_WINDOWS_ROOT = "C:\\Windows"
        const val PROCESS_STOP_GRACE_MILLIS = 1_000L
    }
}

/**
 * Windows pairing implementation backed by WinRT `DeviceInformation.Pairing.PairAsync()`.
 *
 * Kable 0.42's JVM/btleplug bridge does not expose a pairing operation. Calling PairAsync before Kable connects lets
 * Windows present its trusted PIN UI and establish the authenticated bond required by Meshtastic characteristics. macOS
 * and Linux retain the existing direct-connect behavior.
 */
@Single(binds = [DesktopBluetoothPairingService::class])
class JvmDesktopBluetoothPairingService(
    private val dispatchers: CoroutineDispatchers,
    private val processRunner: WindowsPairingProcessRunner,
    private val platform: DesktopBluetoothPlatform,
) : DesktopBluetoothPairingService {
    override val isExplicitPairingRequired: Boolean = platform.isWindows

    override suspend fun ensurePaired(address: String): DesktopBluetoothPairingOutcome {
        if (!isExplicitPairingRequired) return DesktopBluetoothPairingOutcome.NOT_REQUIRED

        val normalizedAddress = normalizeBluetoothAddress(address)
        val script = buildWindowsPairingScript(normalizedAddress)
        val encodedCommand = Base64.getEncoder().encodeToString(script.toByteArray(StandardCharsets.UTF_16LE))
        val result = runPairingProcess(encodedCommand)

        if (result.timedOut) {
            throw BlePairingException(
                failure = BlePairingFailure.TIMED_OUT,
                message = "Windows Bluetooth pairing timed out. Select the device and try again.",
            )
        }

        return pairingOutcomeFrom(result.output)
    }

    private suspend fun runPairingProcess(encodedCommand: String): WindowsPairingProcessResult {
        val result = runCatching {
            withContext(dispatchers.io) { processRunner.run(encodedCommand, PAIRING_TIMEOUT_MILLIS) }
        }
        val failure = result.exceptionOrNull()
        if (failure is CancellationException) throw failure
        if (failure is Exception) throw pairingProcessFailure(failure)
        return result.getOrThrow()
    }

    private fun pairingProcessFailure(cause: Exception) = BlePairingException(
        failure = BlePairingFailure.PLATFORM_FAILURE,
        message = "Windows could not start Bluetooth pairing. Select the device and try again.",
        cause = cause,
    )

    internal companion object {
        private const val PAIRING_TIMEOUT_MILLIS = 120_000L
        private val ADDRESS_PATTERN = Regex("^[0-9A-F]{12}$")
        private val STATUS_PATTERN = Regex("""PAIRING_STATUS=([A-Za-z]+)""")

        internal fun normalizeBluetoothAddress(address: String): String {
            val normalized = address.filterNot { it == ':' || it == '-' }.uppercase()
            if (!ADDRESS_PATTERN.matches(normalized)) {
                throw BlePairingException(
                    failure = BlePairingFailure.PLATFORM_FAILURE,
                    message = "The selected Bluetooth address is invalid.",
                )
            }
            return normalized
        }

        internal fun pairingOutcomeFrom(output: String): DesktopBluetoothPairingOutcome {
            val status =
                STATUS_PATTERN.find(output)?.groupValues?.get(1)
                    ?: throw BlePairingException(
                        failure = BlePairingFailure.PLATFORM_FAILURE,
                        message = "Windows did not return a Bluetooth pairing result.",
                    )

            return when (status) {
                "Paired" -> DesktopBluetoothPairingOutcome.PAIRED
                "AlreadyPaired" -> DesktopBluetoothPairingOutcome.ALREADY_PAIRED
                else -> throw pairingFailure(status)
            }
        }

        private fun pairingFailure(status: String): BlePairingException = when (status) {
            "NotFound" ->
                BlePairingException(
                    failure = BlePairingFailure.DEVICE_NOT_FOUND,
                    message = "Windows could not find the selected Bluetooth device. Scan and try again.",
                )

            "NotReadyToPair",
            "OperationAlreadyInProgress",
            ->
                BlePairingException(
                    failure = BlePairingFailure.NOT_READY,
                    message = "The Bluetooth device is not ready to pair. Wait a moment and select it again.",
                )

            "PairingCanceled" ->
                BlePairingException(
                    failure = BlePairingFailure.CANCELED,
                    message = "Bluetooth pairing was canceled. Select the device to try again.",
                )

            "ConnectionRejected",
            "RejectedByHandler",
            ->
                BlePairingException(
                    failure = BlePairingFailure.REJECTED,
                    message = "The Bluetooth device rejected pairing. Confirm its pairing mode and try again.",
                )

            "AuthenticationTimeout" ->
                BlePairingException(
                    failure = BlePairingFailure.TIMED_OUT,
                    message = "Bluetooth PIN authentication timed out. Select the device to try again.",
                )

            "AuthenticationFailure",
            "ProtectionLevelCouldNotBeMet",
            ->
                BlePairingException(
                    failure = BlePairingFailure.AUTHENTICATION_FAILED,
                    message = "Bluetooth PIN authentication failed. Confirm the PIN and try again.",
                )

            "AccessDenied",
            "AuthenticationNotAllowed",
            ->
                BlePairingException(
                    failure = BlePairingFailure.ACCESS_DENIED,
                    message = "Windows denied Bluetooth pairing access.",
                )

            else ->
                BlePairingException(
                    failure = BlePairingFailure.PLATFORM_FAILURE,
                    message = "Windows Bluetooth pairing failed ($status).",
                )
        }

        internal fun buildWindowsPairingScript(normalizedAddress: String): String =
            """
            ${'$'}ErrorActionPreference = 'Stop'
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            ${'$'}null = [Windows.Devices.Bluetooth.BluetoothLEDevice, Windows.Devices.Bluetooth, ContentType = WindowsRuntime]
            ${'$'}null = [Windows.Devices.Enumeration.DevicePairingResult, Windows.Devices.Enumeration, ContentType = WindowsRuntime]
            ${'$'}asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
                ${'$'}_.Name -eq 'AsTask' -and ${'$'}_.IsGenericMethod -and ${'$'}_.GetParameters().Count -eq 1
            })[0]
            function Await-WinRt(${ '$' }operation, [Type] ${'$'}resultType) {
                ${'$'}task = ${'$'}asTask.MakeGenericMethod(${ '$' }resultType).Invoke(${ '$' }null, @(${ '$' }operation))
                ${'$'}task.Wait()
                ${'$'}task.Result
            }
            ${'$'}device = ${'$'}null
            try {
                ${'$'}address = [Convert]::ToUInt64('$normalizedAddress', 16)
                ${'$'}operation = [Windows.Devices.Bluetooth.BluetoothLEDevice]::FromBluetoothAddressAsync(${ '$' }address)
                ${'$'}device = Await-WinRt ${'$'}operation ([Windows.Devices.Bluetooth.BluetoothLEDevice])
                if (${ '$' }null -eq ${'$'}device) {
                    Write-Output 'PAIRING_STATUS=NotFound'
                    exit 4
                }
                ${'$'}pairing = ${'$'}device.DeviceInformation.Pairing
                if (${ '$' }pairing.IsPaired) {
                    Write-Output 'PAIRING_STATUS=AlreadyPaired'
                    exit 0
                }
                if (-not ${'$'}pairing.CanPair) {
                    Write-Output 'PAIRING_STATUS=NotReadyToPair'
                    exit 5
                }
                ${'$'}pairOperation = ${'$'}pairing.PairAsync()
                ${'$'}result = Await-WinRt ${'$'}pairOperation ([Windows.Devices.Enumeration.DevicePairingResult])
                Write-Output ('PAIRING_STATUS=' + ${'$'}result.Status.ToString())
                if (${ '$' }result.Status.ToString() -in @('Paired', 'AlreadyPaired')) { exit 0 }
                exit 6
            } catch {
                Write-Output 'PAIRING_STATUS=Failed'
                exit 9
            } finally {
                if (${ '$' }null -ne ${'$'}device) { ${'$'}device.Dispose() }
            }
            """
                .trimIndent()
    }
}
