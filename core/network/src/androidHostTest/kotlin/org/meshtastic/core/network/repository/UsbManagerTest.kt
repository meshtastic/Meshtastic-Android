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

import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UsbManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        shadowOf(context.applicationContext as Application).clearRegisteredReceivers()
    }

    @Test
    fun `permission result receiver is private and ignores a different device`() = runTest {
        val requestedDevice = usbDevice("/dev/bus/usb/001/001")
        val differentDevice = usbDevice("/dev/bus/usb/001/002")
        var platformHasPermission = false

        val result =
            async(start = CoroutineStart.UNDISPATCHED) {
                usbPermissionResultFlow(
                    context = context,
                    device = requestedDevice,
                    hasPermission = { platformHasPermission },
                    requestPermission = {},
                )
                    .first()
            }
        runCurrent()

        val registration = usbPermissionRegistrations().single()
        assertEquals(ContextCompat.RECEIVER_NOT_EXPORTED, registration.flags)

        registration.broadcastReceiver.onReceive(
            context,
            permissionResultIntent(registration.intentFilter.getAction(0), differentDevice, true),
        )
        runCurrent()
        assertFalse(result.isCompleted)

        registration.broadcastReceiver.onReceive(
            context,
            permissionResultIntent(registration.intentFilter.getAction(0), requestedDevice, true),
        )
        runCurrent()
        assertFalse(result.await())
    }

    @Test
    fun `permission result requires the platform grant`() = runTest {
        val requestedDevice = usbDevice("/dev/bus/usb/001/003")
        var requestCalls = 0

        val result =
            async(start = CoroutineStart.UNDISPATCHED) {
                usbPermissionResultFlow(
                    context = context,
                    device = requestedDevice,
                    hasPermission = { true },
                    requestPermission = { requestCalls += 1 },
                )
                    .first()
            }
        runCurrent()

        assertEquals(1, requestCalls)
        val registration = usbPermissionRegistrations().single()
        registration.broadcastReceiver.onReceive(
            context,
            permissionResultIntent(registration.intentFilter.getAction(0), requestedDevice, true),
        )
        runCurrent()

        assertTrue(result.await())
    }

    @Test
    fun `permission result rejects a broadcast grant when platform permission is absent`() = runTest {
        val requestedDevice = usbDevice("/dev/bus/usb/001/007")
        var requestCalls = 0

        val result =
            async(start = CoroutineStart.UNDISPATCHED) {
                usbPermissionResultFlow(
                    context = context,
                    device = requestedDevice,
                    hasPermission = { false },
                    requestPermission = { requestCalls += 1 },
                )
                    .first()
            }
        runCurrent()

        assertEquals(1, requestCalls)
        val registration = usbPermissionRegistrations().single()
        registration.broadcastReceiver.onReceive(
            context,
            permissionResultIntent(registration.intentFilter.getAction(0), requestedDevice, true),
        )
        runCurrent()

        assertFalse(result.await())
    }

    @Test
    fun `permission result without a device is denied and completes`() = runTest {
        val requestedDevice = usbDevice("/dev/bus/usb/001/006")
        var platformPermissionChecked = false

        val result =
            async(start = CoroutineStart.UNDISPATCHED) {
                usbPermissionResultFlow(
                    context = context,
                    device = requestedDevice,
                    hasPermission = {
                        platformPermissionChecked = true
                        true
                    },
                    requestPermission = {},
                )
                    .first()
            }
        runCurrent()

        val registration = usbPermissionRegistrations().single()
        registration.broadcastReceiver.onReceive(
            context,
            Intent(registration.intentFilter.getAction(0))
                .setPackage(context.packageName)
                .putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, true),
        )
        runCurrent()

        assertFalse(result.await())
        assertFalse(platformPermissionChecked)
        runCurrent()
        assertTrue(usbPermissionRegistrations().isEmpty())
    }

    @Test
    fun `concurrent requests consume only their own device result`() = runTest {
        val firstDevice = usbDevice("/dev/bus/usb/001/004")
        val secondDevice = usbDevice("/dev/bus/usb/001/005")

        val firstResult =
            async(start = CoroutineStart.UNDISPATCHED) {
                usbPermissionResultFlow(context, firstDevice, hasPermission = { true }, requestPermission = {}).first()
            }
        val secondResult =
            async(start = CoroutineStart.UNDISPATCHED) {
                usbPermissionResultFlow(context, secondDevice, hasPermission = { true }, requestPermission = {}).first()
            }
        runCurrent()

        val action = usbPermissionRegistrations().first().intentFilter.getAction(0)
        dispatchPermissionResult(action, firstDevice)
        runCurrent()
        assertTrue(firstResult.await())
        assertFalse(secondResult.isCompleted)

        dispatchPermissionResult(action, secondDevice)
        runCurrent()
        assertTrue(secondResult.await())
    }

    private fun permissionResultIntent(action: String, device: UsbDevice, granted: Boolean): Intent = Intent(action)
        .setPackage(context.packageName)
        .putExtra(UsbManager.EXTRA_DEVICE, device)
        .putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, granted)

    private fun dispatchPermissionResult(action: String, device: UsbDevice) {
        val intent = permissionResultIntent(action, device, true)
        usbPermissionRegistrations().forEach { registration ->
            registration.broadcastReceiver.onReceive(context, intent)
        }
    }

    private fun usbPermissionRegistrations() =
        shadowOf(context.applicationContext as Application).registeredReceivers.filter {
            it.intentFilter.hasAction(ACTION_USB_PERMISSION)
        }

    private fun usbDevice(name: String): UsbDevice =
        Shadow.newInstanceOf(UsbDevice::class.java).also { ReflectionHelpers.setField(it, "mName", name) }
}
