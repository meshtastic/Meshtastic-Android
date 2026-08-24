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
package org.meshtastic.feature.firmware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import org.meshtastic.core.network.repository.UsbRepository

/** Manages USB-related interactions for firmware updates. */
@Single
class AndroidFirmwareUsbManager(private val context: Context, private val usbRepository: UsbRepository) :
    FirmwareUsbManager {

    override suspend fun serialPortKeys(): Set<String> = usbRepository.serialDevices.value.keys

    @Suppress("ReturnCount") // each failed precondition must abort without poking an arbitrary port
    override suspend fun unblockCdcPort(excluding: Set<String>, waitMillis: Long, holdMillis: Long): Boolean {
        // The erase image enumerates a moment after the write, so poll rather than sampling once.
        val driver =
            withTimeoutOrNull(waitMillis) {
                usbRepository.serialDevices
                    .map { devices -> devices.entries.firstOrNull { it.key !in excluding }?.value }
                    .filterNotNull()
                    .first()
            }

        if (driver == null) {
            Logger.w { "No new serial port appeared within ${waitMillis}ms; cannot unblock the erase image" }
            return false
        }

        // device_filter.xml lists no bootloader-mode ids (and none at all for Seeed's 0x2886), so no implicit grant
        // exists here — an explicit request is the only route. Bounded by waitMillis too: requestPermission can
        // return emptyFlow() (no UsbManager available), and first() on that throws instead of denying cleanly —
        // firstOrNull() under a timeout treats both "empty flow" and "unanswered dialog" as a plain denial.
        if (
            !usbRepository.hasPermission(driver.device) &&
            withTimeoutOrNull(waitMillis) { usbRepository.requestPermission(driver.device).firstOrNull() } != true
        ) {
            Logger.w { "USB permission denied; cannot unblock the erase image" }
            return false
        }

        return usbRepository.pokeDtr(driver, holdMillis)
    }

    override suspend fun ensureSerialPermission(waitMillis: Long): Boolean = withTimeoutOrNull(waitMillis) {
        // The updated firmware enumerates a while after the UF2 is consumed; poll rather than sampling once.
        val driver =
            usbRepository.serialDevices.map { devices -> devices.values.firstOrNull() }.filterNotNull().first()
        if (usbRepository.hasPermission(driver.device)) return@withTimeoutOrNull true
        // The permission dialog itself must share this budget — otherwise an unanswered dialog leaves the
        // caller (the post-update verification flow) parked forever instead of falling back to auto-recovery.
        // firstOrNull(): requestPermission can return emptyFlow() (no UsbManager available), and first() on
        // that throws instead of denying cleanly.
        usbRepository.requestPermission(driver.device).firstOrNull() == true
    }
        ?: run {
            Logger.w { "USB permission preflight did not complete within ${waitMillis}ms" }
            false
        }

    /** Observe when a USB device is detached. */
    override fun deviceDetachFlow(): Flow<Unit> = callbackFlow {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                        trySend(Unit).isSuccess
                        close()
                    }
                }
            }
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
                .onFailure { Logger.w(it) { "Failed to unregister USB receiver" } }
        }
    }
}
