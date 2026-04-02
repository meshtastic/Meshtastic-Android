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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.IInterface
import dev.mokkery.MockMode
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.matcher.capture.Capture
import dev.mokkery.matcher.capture.capture
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.exactly
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceClientTest {

    interface MyInterface : IInterface

    private val stubFactory: (IBinder) -> MyInterface = { _ -> mock<MyInterface>() }
    private val client = ServiceClient(stubFactory)
    private val context = mock<Context>(MockMode.autofill)
    private val intent = mock<Intent>()
    private val binder = mock<IBinder>()

    @Test
    fun `connect binds service successfully`() = runTest {
        val slot = Capture.slot<ServiceConnection>()
        every { context.bindService(any(), capture(slot), any()) } returns true

        client.connect(context, intent, 0)

        verify { context.bindService(intent, any(), 0) }

        // Simulate connection
        try {
            slot.get().onServiceConnected(ComponentName("pkg", "cls"), binder)
            assertNotNull(client.serviceP)
        } catch (e: NoSuchElementException) {
            fail("ServiceConnection was not captured")
        }
    }

    @Test
    fun `connect retries on failure`() = runTest {
        val slot = Capture.slot<ServiceConnection>()
        // First attempt fails, second succeeds
        every { context.bindService(any(), capture(slot), any()) } sequentially
            {
                returns(false)
                returns(true)
            }

        client.connect(context, intent, 0)

        verify(exactly(2)) { context.bindService(intent, any(), 0) }
    }

    @Test
    fun `connect throws exception after two failures`() = runTest {
        every { context.bindService(any(), any(), any()) } returns false
        assertFailsWith<BindFailedException> { client.connect(context, intent, 0) }
    }

    @Test
    fun `waitConnect blocks until connected`() {
        val slot = Capture.slot<ServiceConnection>()
        every { context.bindService(any(), capture(slot), any()) } returns true

        // Run connect in a coroutine scope (it's suspend)
        runTest { client.connect(context, intent, 0) }

        val latch = CountDownLatch(1)
        thread {
            client.waitConnect()
            latch.countDown()
        }

        // Verify it's blocked (wait a bit)
        if (latch.await(100, TimeUnit.MILLISECONDS)) {
            fail("waitConnect should block until connected")
        }

        // Simulate connection
        try {
            slot.get().onServiceConnected(ComponentName("pkg", "cls"), binder)
        } catch (e: NoSuchElementException) {
            fail("ServiceConnection was not captured")
        }

        // Verify it unblocks
        if (!latch.await(1, TimeUnit.SECONDS)) {
            fail("waitConnect should unblock after connection")
        }

        assertNotNull(client.serviceP)
    }

    @Test
    fun `close unbinds service`() = runTest {
        val slot = Capture.slot<ServiceConnection>()
        every { context.bindService(any(), capture(slot), any()) } returns true

        client.connect(context, intent, 0)

        try {
            client.close()
            verify { context.unbindService(slot.get()) }
            assertNull(client.serviceP)
        } catch (e: NoSuchElementException) {
            fail("ServiceConnection was not captured")
        }
    }
}
