package org.meshtastic.core.ble

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import no.nordicsemi.kotlin.ble.client.RemoteServices
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import org.junit.Test
import kotlin.uuid.Uuid

class BleConnectionTest {
    @Test(expected = Exception::class)
    fun `discoverCharacteristics throws exception when service discovery fails`() = runTest {
        val centralManager = mockk<no.nordicsemi.kotlin.ble.client.android.CentralManager>(relaxed = true)
        val peripheral = mockk<Peripheral>(relaxed = true)
        val bleConnection = BleConnection(centralManager, this)
        
        // Mock peripheral property (internal access)
        val peripheralField = BleConnection::class.java.getDeclaredField("peripheral")
        peripheralField.isAccessible = true
        peripheralField.set(bleConnection, peripheral)

        val serviceUuid = Uuid.random()
        val servicesFlow = MutableStateFlow<RemoteServices>(RemoteServices.Failed(RemoteServices.Failed.Reason.EmptyResult))
        
        every { peripheral.services(any()) } returns servicesFlow

        bleConnection.discoverCharacteristics(serviceUuid, listOf(Uuid.random()))
    }
}
