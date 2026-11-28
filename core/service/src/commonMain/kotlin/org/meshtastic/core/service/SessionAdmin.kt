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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineStart
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.sdk.AdminApi
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.RadioClient

/** Runs admin commands on the local node through the active radio session's SDK admin API. */
@Single
internal class SessionAdmin(private val radio: RadioInterfaceService, private val scope: ServiceScope) {

    /**
     * Starts [command] without waiting for the device's answer and logs a failure. The SDK enqueues the packet before
     * [command] first suspends, so commands started in order reach the radio in order.
     *
     * @throws PacketQueueRejectedException when no radio session is active.
     */
    fun launch(
        operation: String,
        onResult: suspend (AdminResult<*>) -> Unit = {},
        command: suspend AdminApi.() -> AdminResult<*>,
    ) {
        val admin = requireAdmin(operation)
        scope.handledLaunch(start = CoroutineStart.UNDISPATCHED) {
            val result = runCommand(admin, command)
            if (result !is AdminResult.Success) Logger.w { "$operation was not applied: $result" }
            onResult(result)
        }
    }

    /** Runs [command] and returns the device's answer, or [AdminResult.NodeUnreachable] without a radio session. */
    suspend fun <T> run(command: suspend AdminApi.() -> AdminResult<T>): AdminResult<T> {
        val admin = radio.activeSession.value?.let(radio::adminForSession) ?: return AdminResult.NodeUnreachable
        return runCommand(admin, command)
    }

    /**
     * Runs [command] and returns the device's answer, or [AdminResult.NodeUnreachable] when the session ends first.
     *
     * @throws PacketQueueRejectedException when no radio session is active.
     */
    suspend fun <T> await(operation: String, command: suspend AdminApi.() -> AdminResult<T>): AdminResult<T> =
        runCommand(requireAdmin(operation), command)

    /** The active session's SDK client, or null when no radio session is active. */
    fun activeClient(): RadioClient? = radio.activeSession.value?.let(radio::clientForSession)

    private fun requireAdmin(operation: String): AdminApi =
        radio.activeSession.value?.let(radio::adminForSession) ?: throw PacketQueueRejectedException(operation)

    private suspend fun <T> runCommand(admin: AdminApi, command: suspend AdminApi.() -> AdminResult<T>) = try {
        admin.command()
    } catch (e: MeshtasticException) {
        Logger.w(e) { "Admin command ended with the radio session" }
        AdminResult.NodeUnreachable
    }
}
