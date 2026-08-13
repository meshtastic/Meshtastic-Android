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

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MeshPrefs
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBroadcastPendingResult
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootCompleteReceiverTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `persisted selected address starts service exactly once after load`() = runTest {
        val persistedAddress = CompletableDeferred<String?>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        installDependencies(DeferredMeshPrefs { persistedAddress.await() }, dispatcher)
        val application = testApplication()

        val pendingResult = dispatchBootCompleted(BootCompleteReceiver(), application)
        runCurrent()

        assertFalse(pendingResult.future.isDone)
        assertTrue(shadowOf(application).allStartedServices.isEmpty())

        persistedAddress.complete("xAA:BB:CC:DD:EE:FF")
        runCurrent()

        assertTrue(pendingResult.future.isDone)
        val startedServices = shadowOf(application).allStartedServices
        assertEquals(1, startedServices.size)
        assertEquals(MeshService::class.java.name, startedServices.single().component?.className)
    }

    @Test
    fun `persisted no-device sentinel does not start service after load`() = runTest {
        val persistedAddress = CompletableDeferred<String?>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        installDependencies(DeferredMeshPrefs { persistedAddress.await() }, dispatcher)
        val application = testApplication()

        val pendingResult = dispatchBootCompleted(BootCompleteReceiver(), application)
        runCurrent()
        persistedAddress.complete("n")
        runCurrent()

        assertTrue(pendingResult.future.isDone)
        assertTrue(shadowOf(application).allStartedServices.isEmpty())
    }

    @Test
    fun `pending result finishes when persistence load times out`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        installDependencies(DeferredMeshPrefs { awaitCancellation() }, dispatcher)
        val application = testApplication()

        val pendingResult = dispatchBootCompleted(BootCompleteReceiver(), application)
        runCurrent()
        advanceUntilIdle()

        assertTrue(pendingResult.future.isDone)
        assertTrue(shadowOf(application).allStartedServices.isEmpty())
    }

    @Test
    fun `timeout includes time queued for IO dispatcher`() = runTest {
        val ioDispatcher = StallingDispatcher()
        val timeoutDispatcher = StandardTestDispatcher(testScheduler)
        var persistenceReadStarted = false
        val meshPrefs = DeferredMeshPrefs {
            persistenceReadStarted = true
            awaitCancellation()
        }
        installDependencies(meshPrefs, ioDispatcher, timeoutDispatcher)
        val application = testApplication()

        val pendingResult = dispatchBootCompleted(BootCompleteReceiver(), application)

        assertEquals(1, ioDispatcher.queuedTaskCount)
        assertFalse(persistenceReadStarted)
        assertFalse(pendingResult.future.isDone)

        advanceTimeBy(4_999)
        runCurrent()
        assertFalse(pendingResult.future.isDone)

        advanceTimeBy(1)
        runCurrent()

        assertTrue(pendingResult.future.isDone)
        assertFalse(persistenceReadStarted)
        assertTrue(shadowOf(application).allStartedServices.isEmpty())
    }

    @Test
    fun `pending result finishes when persistence load fails`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        installDependencies(DeferredMeshPrefs { throw IOException("read failed") }, dispatcher)
        val application = testApplication()

        val pendingResult = dispatchBootCompleted(BootCompleteReceiver(), application)
        runCurrent()

        assertTrue(pendingResult.future.isDone)
        assertTrue(shadowOf(application).allStartedServices.isEmpty())
    }

    private fun installDependencies(
        meshPrefs: MeshPrefs,
        ioDispatcher: CoroutineDispatcher,
        defaultDispatcher: CoroutineDispatcher = ioDispatcher,
    ) {
        startKoin {
            modules(
                module {
                    single { meshPrefs }
                    single {
                        CoroutineDispatchers(io = ioDispatcher, main = defaultDispatcher, default = defaultDispatcher)
                    }
                },
            )
        }
    }

    private fun testApplication(): Application =
        ApplicationProvider.getApplicationContext<Application>().also { shadowOf(it).clearStartedServices() }

    private fun dispatchBootCompleted(
        receiver: BootCompleteReceiver,
        application: Application,
    ): ShadowBroadcastPendingResult {
        val pendingResult =
            ReflectionHelpers.callStaticMethod<BroadcastReceiver.PendingResult>(
                ShadowBroadcastPendingResult::class.java,
                "create",
                ClassParameter.from(Int::class.javaPrimitiveType!!, 0),
                ClassParameter.from(String::class.java, ""),
                ClassParameter.from(Bundle::class.java, Bundle()),
                ClassParameter.from(Boolean::class.javaPrimitiveType!!, false),
            )
        ReflectionHelpers.callInstanceMethod<Any?>(
            receiver,
            "setPendingResult",
            ClassParameter.from(BroadcastReceiver.PendingResult::class.java, pendingResult),
        )

        receiver.onReceive(application, Intent(Intent.ACTION_BOOT_COMPLETED))

        return Shadow.extract(pendingResult)
    }

    private class DeferredMeshPrefs(private val loadDeviceAddress: suspend () -> String?) : MeshPrefs {
        override val deviceAddress = MutableStateFlow<String?>(null)

        override fun setDeviceAddress(address: String?) {
            deviceAddress.value = address
        }

        override suspend fun awaitDeviceAddress(): String? = loadDeviceAddress()

        override fun getStoreForwardLastRequest(address: String?): StateFlow<Int> = MutableStateFlow(0)

        override fun setStoreForwardLastRequest(address: String?, timestamp: Int) = Unit
    }

    private class StallingDispatcher : CoroutineDispatcher() {
        private val queuedTasks = mutableListOf<Runnable>()
        val queuedTaskCount: Int
            get() = queuedTasks.size

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queuedTasks += block
        }
    }
}
