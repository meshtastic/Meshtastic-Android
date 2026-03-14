package org.meshtastic.feature.firmware

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.RadioController
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KableNordicDfuHandlerTest {

    private lateinit var handler: KableNordicDfuHandler
    private lateinit var firmwareRetriever: FirmwareRetriever
    private lateinit var radioController: RadioController
    private lateinit var bleScanner: BleScanner
    private lateinit var bleConnectionFactory: BleConnectionFactory
    private lateinit var context: Context

    @Before
    fun setUp() {
        firmwareRetriever = mockk(relaxed = true)
        radioController = mockk(relaxed = true)
        bleScanner = mockk(relaxed = true)
        bleConnectionFactory = mockk(relaxed = true)
        context = mockk(relaxed = true)

        handler = KableNordicDfuHandler(
            firmwareRetriever = firmwareRetriever,
            radioController = radioController,
            bleScanner = bleScanner,
            bleConnectionFactory = bleConnectionFactory,
            context = context
        )
    }

    @Test
    fun testStartUpdate_triggersFirmwareUpdate() = runTest {
        val release = mockk<FirmwareRelease>(relaxed = true)
        val hardware = mockk<DeviceHardware>(relaxed = true) {
            every { displayName } returns "test_hw"
        }
        
        // Mock a zip file being returned
        val tempZip = File.createTempFile("test_firmware", ".zip")
        // Create an empty zip file
        java.util.zip.ZipOutputStream(tempZip.outputStream()).use { }
        
        coEvery { firmwareRetriever.retrieveOtaFirmware(any(), any(), any()) } returns tempZip.absolutePath
        
        val states = mutableListOf<FirmwareUpdateState>()
        
        val job = launch {
            handler.startUpdate(release, hardware, "AA:BB:CC:DD:EE:FF", { state ->
                states.add(state)
            }, null)
        }
        
        advanceUntilIdle()
        job.join()
        
        tempZip.delete()
        
        assertTrue(states.any { it is FirmwareUpdateState.Processing }, "Should process")
        assertTrue(states.any { it is FirmwareUpdateState.Success }, "Should finish with success")
    }
}
