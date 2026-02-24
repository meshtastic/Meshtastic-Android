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
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceClientTest {

    interface MyInterface : IInterface

    private val stubFactory: (IBinder) -> MyInterface = { _ -> mockk<MyInterface>() }
    private val client = ServiceClient(stubFactory)
    private val context = mockk<Context>(relaxed = true)
    private val intent = mockk<Intent>()
    private val binder = mockk<IBinder>()

    @Test
    fun `connect binds service successfully`() = runTest {
        val slot = slot<ServiceConnection>()
        every { context.bindService(any<Intent>(), capture(slot), any<Int>()) } returns true

        client.connect(context, intent, 0)

        verify { context.bindService(intent, any<ServiceConnection>(), 0) }

        // Simulate connection
        if (slot.isCaptured) {
            slot.captured.onServiceConnected(ComponentName("pkg", "cls"), binder)
            assertNotNull(client.serviceP)
        } else {
            fail("ServiceConnection was not captured")
        }
    }

    @Test
    fun `connect retries on failure`() = runTest {
        val slot = slot<ServiceConnection>()
        // First attempt fails, second succeeds
        every { context.bindService(any<Intent>(), capture(slot), any<Int>()) } returnsMany listOf(false, true)

        client.connect(context, intent, 0)

        verify(exactly = 2) { context.bindService(intent, any<ServiceConnection>(), 0) }
    }

    @Test(expected = BindFailedException::class)
    fun `connect throws exception after two failures`() = runTest {
        every { context.bindService(any<Intent>(), any<ServiceConnection>(), any<Int>()) } returns false
        client.connect(context, intent, 0)
    }

    @Test
    fun `waitConnect blocks until connected`() {
        val slot = slot<ServiceConnection>()
        every { context.bindService(any<Intent>(), capture(slot), any<Int>()) } returns true

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
        if (slot.isCaptured) {
            slot.captured.onServiceConnected(ComponentName("pkg", "cls"), binder)
        } else {
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
        val slot = slot<ServiceConnection>()
        every { context.bindService(any<Intent>(), capture(slot), any<Int>()) } returns true

        client.connect(context, intent, 0)

        if (slot.isCaptured) {
            client.close()
            verify { context.unbindService(slot.captured) }
            assertNull(client.serviceP)
        } else {
            fail("ServiceConnection was not captured")
        }
    }
}
