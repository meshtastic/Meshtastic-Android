package org.meshtastic.core.ble

import com.juul.kable.State
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KableStateMappingTest {

    @Test
    fun `Connecting maps to Connecting`() {
        val state = mockk<State.Connecting>()
        val result = state.toBleConnectionState(hasStartedConnecting = false)
        assertEquals(BleConnectionState.Connecting, result)
    }

    @Test
    fun `Connected maps to Connected`() {
        val state = mockk<State.Connected>()
        val result = state.toBleConnectionState(hasStartedConnecting = true)
        assertEquals(BleConnectionState.Connected, result)
    }

    @Test
    fun `Disconnecting maps to Disconnecting`() {
        val state = mockk<State.Disconnecting>()
        val result = state.toBleConnectionState(hasStartedConnecting = true)
        assertEquals(BleConnectionState.Disconnecting, result)
    }

    @Test
    fun `Disconnected ignores initial emission if not started connecting`() {
        val state = mockk<State.Disconnected>()
        val result = state.toBleConnectionState(hasStartedConnecting = false)
        assertNull(result)
    }

    @Test
    fun `Disconnected maps to Disconnected if started connecting`() {
        val state = mockk<State.Disconnected>()
        val result = state.toBleConnectionState(hasStartedConnecting = true)
        assertEquals(BleConnectionState.Disconnected, result)
    }
}
