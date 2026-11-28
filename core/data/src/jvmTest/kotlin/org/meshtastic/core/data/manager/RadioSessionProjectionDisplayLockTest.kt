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
package org.meshtastic.core.data.manager

import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.repository.DisplayMirrorManager
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.repository.MirrorPalette
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.proto.DisplayFrame
import org.meshtastic.proto.DisplayPalette
import org.meshtastic.sdk.DisplayMirrorFrame
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins that session admission never waits on a display frame handler: a frame being composited holds no lock that
 * `isSessionActive` needs. Regression: `RadioSessionProjection` calling `handleFrame` inside `runIfSessionActive`.
 */
class RadioSessionProjectionDisplayLockTest {

    @Test
    fun `a frame being composited does not block session admission checks`() {
        val scope = CoroutineScope(SupervisorJob())
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val mirror = object : DisplayMirrorManager {
            override val frame: StateFlow<MirrorFrame?> = MutableStateFlow(null)
            override val palette: StateFlow<MirrorPalette?> = MutableStateFlow(null)

            override fun handleFrame(frame: DisplayMirrorFrame) {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
            }

            override fun handlePalette(palette: DisplayPalette) = Unit

            override fun reset() = Unit
        }
        val radio = FakeRadioInterfaceService(scope).apply {
            setDeviceAddress("ble:a")
            connect()
        }
        val session = checkNotNull(radio.activeSession.value)
        val nodeManager = NodeManagerImpl(FakeNodeRepository(), mock(MockMode.autofill), radio, scope.asServiceScope())
        val projection = radioSessionProjection(
            nodeManager = nodeManager,
            radio = radio,
            scope = scope,
            displayMirrorManager = mirror,
        )
        val frame = DisplayMirrorFrame(
            width = 128,
            height = 64,
            format = DisplayFrame.Format.MONO_VLSB,
            frameId = 1,
            data = ByteArray(1024).toByteString(),
            paletteSignature = 0,
        )
        val compositor = Thread { projection.onDisplayFrame(frame, session) }
        try {
            compositor.start()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val admitted = CompletableFuture.supplyAsync { radio.isSessionActive(session) }.get(2, TimeUnit.SECONDS)
            assertTrue(admitted)
        } finally {
            release.countDown()
            compositor.join(5_000)
            scope.cancel()
        }
    }
}
