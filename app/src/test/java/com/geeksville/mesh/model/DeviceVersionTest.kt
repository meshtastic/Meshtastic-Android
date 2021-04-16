package com.geeksville.mesh.model

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test

class DeviceVersionTest {
    /** make sure we match the python and device code behavior */
    @Test
    fun canParse() {

        assertEquals(10000, DeviceVersion("1.0.0").asInt)
        assertEquals(10101, DeviceVersion("1.1.1").asInt)
        assertEquals(12357, DeviceVersion("1.23.57").asInt)
        assertEquals(12357, DeviceVersion("1.23.57.abde123").asInt)
    }
}