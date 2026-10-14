package org.meshtastic.core.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GabagoolReconnectPolicyTest {
    @Test
    fun backoffIsBounded() {
        assertEquals(1_000L, GabagoolReconnectPolicy.delayMillis(1))
        assertEquals(2_000L, GabagoolReconnectPolicy.delayMillis(2))
        assertEquals(4_000L, GabagoolReconnectPolicy.delayMillis(3))
        assertEquals(60_000L, GabagoolReconnectPolicy.delayMillis(20))
        assertEquals(60_000L, GabagoolReconnectPolicy.delayMillis(Int.MAX_VALUE))
    }

    @Test
    fun explicitDisconnectPreventsRetry() {
        assertFalse(GabagoolReconnectPolicy.shouldRetry(userDisconnected = true, shuttingDown = false))
        assertFalse(GabagoolReconnectPolicy.shouldRetry(userDisconnected = false, shuttingDown = true))
        assertTrue(GabagoolReconnectPolicy.shouldRetry(userDisconnected = false, shuttingDown = false))
    }
}
