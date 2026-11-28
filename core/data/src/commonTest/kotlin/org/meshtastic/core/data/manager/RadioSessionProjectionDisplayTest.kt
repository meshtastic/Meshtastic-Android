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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.repository.DisplayMirrorManager
import org.meshtastic.core.repository.MirrorFrame
import org.meshtastic.core.repository.MirrorPalette
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.proto.DisplayFrame
import org.meshtastic.proto.DisplayPalette
import org.meshtastic.sdk.DisplayImage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the display mirror admission check: a frame or palette for a session that is no longer admitted must not reach
 * the mirror, and one for the open session must.
 *
 * Regression: dropping or inverting the `isSessionActive` check in `onDisplayImage` or `onDisplayPalette`.
 */
class RadioSessionProjectionDisplayTest {

    private class RecordingMirror : DisplayMirrorManager {
        override val frame: StateFlow<MirrorFrame?> = MutableStateFlow(null)
        override val palette: StateFlow<MirrorPalette?> = MutableStateFlow(null)
        val frames = mutableListOf<DisplayImage>()
        val palettes = mutableListOf<DisplayPalette>()

        override fun handleImage(image: DisplayImage) {
            frames += image
        }

        override fun handlePalette(palette: DisplayPalette) {
            palettes += palette
        }

        override fun reset() = Unit
    }

    private fun frame() = DisplayImage(8, 4, DisplayFrame.Format.RGB565, 1, 0, ByteArray(64).toByteString())

    private fun palette() = DisplayPalette.Builder().also { it.signature = 7 }.build()

    @Test
    fun `a display frame for a closed session is not published`() = runTest {
        val radio = FakeRadioInterfaceService(backgroundScope).apply { setDeviceAddress("ble:a") }
        radio.connect()
        val session = checkNotNull(radio.activeSession.value)
        val mirror = RecordingMirror()
        val projection = projection(radio, mirror)
        radio.disconnect()

        projection.onDisplayImage(frame(), session)

        assertEquals(emptyList(), mirror.frames)
    }

    @Test
    fun `a display frame for the open session is published`() = runTest {
        val radio = FakeRadioInterfaceService(backgroundScope).apply { setDeviceAddress("ble:a") }
        radio.connect()
        val session = checkNotNull(radio.activeSession.value)
        val mirror = RecordingMirror()
        val projection = projection(radio, mirror)
        val sent = frame()

        projection.onDisplayImage(sent, session)

        assertEquals(listOf(sent), mirror.frames)
    }

    @Test
    fun `a palette for a closed session is not published`() = runTest {
        val radio = FakeRadioInterfaceService(backgroundScope).apply { setDeviceAddress("ble:a") }
        radio.connect()
        val session = checkNotNull(radio.activeSession.value)
        val mirror = RecordingMirror()
        val projection = projection(radio, mirror)
        radio.disconnect()

        projection.onDisplayPalette(palette(), session)

        assertEquals(emptyList(), mirror.palettes)
    }

    private fun projection(radio: FakeRadioInterfaceService, mirror: DisplayMirrorManager): RadioSessionProjection {
        val scope = radio.serviceScope
        val nodeManager = NodeManagerImpl(FakeNodeRepository(), mock(MockMode.autofill), radio, scope.asServiceScope())
        return radioSessionProjection(
            nodeManager = nodeManager,
            radio = radio,
            scope = scope,
            displayMirrorManager = mirror,
        )
    }
}
