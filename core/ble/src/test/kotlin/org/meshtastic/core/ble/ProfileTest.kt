package org.meshtastic.core.ble

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import org.junit.Test

class ProfileTest {
    @Test
    fun testProfileApi() = runTest {
        val p = mockk<Peripheral>(relaxed = true)
        // p.profile(serviceUuid = Uuid.random(), required = true) { service -> }
    }
}
