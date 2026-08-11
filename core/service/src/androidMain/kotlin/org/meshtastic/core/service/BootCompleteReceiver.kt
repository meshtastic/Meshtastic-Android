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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.common.util.isValidDeviceAddress
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MeshPrefs

/** This receiver starts the MeshService on boot if a device was previously connected. */
class BootCompleteReceiver :
    BroadcastReceiver(),
    KoinComponent {

    private val meshPrefs: MeshPrefs by inject()
    private val dispatchers: CoroutineDispatchers by inject()
    private val scope by lazy { CoroutineScope(SupervisorJob() + dispatchers.default) }

    @Suppress("TooGenericExceptionCaught")
    override fun onReceive(context: Context, intent: Intent) {
        // Only these two actions carry a background foreground-service-start exemption. The manifest also filters the
        // OEM quick-boot actions, which do not, so acting on those would guarantee a rejected start.
        if (intent.action !in EXEMPT_ACTIONS) {
            Logger.d { "BootCompleteReceiver: ignoring non-exempt action ${intent.action}" }
            return
        }

        val pendingResult = goAsync()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val address =
                    try {
                        withTimeout(PREFERENCES_LOAD_TIMEOUT_MILLIS) {
                            // Keep the IO read as a sibling so a queued dispatcher cannot delay timeout completion.
                            val preferencesLoad = scope.async(dispatchers.io) { meshPrefs.awaitDeviceAddress() }
                            try {
                                preferencesLoad.await()
                            } finally {
                                preferencesLoad.cancel()
                            }
                        }
                    } catch (_: TimeoutCancellationException) {
                        Logger.w { "BootCompleteReceiver: timed out loading the selected device" }
                        return@launch
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.w(e) { "BootCompleteReceiver: failed to load the selected device" }
                        return@launch
                    }

                if (!isValidDeviceAddress(address)) {
                    Logger.d { "BootCompleteReceiver: no device previously connected, skipping service start" }
                    return@launch
                }

                Logger.i { "BootCompleteReceiver: starting MeshService after ${intent.action}" }
                MeshService.startService(context, ServiceStartTrigger.BootCompleted)
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private companion object {
        const val PREFERENCES_LOAD_TIMEOUT_MILLIS = 5_000L

        /**
         * `MY_PACKAGE_REPLACED` is filtered by the manifest so an in-place upgrade restores the radio link without
         * waiting for the user to reopen the app, and it is exempt from the background-start restriction just as
         * `BOOT_COMPLETED` is.
         */
        val EXEMPT_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
