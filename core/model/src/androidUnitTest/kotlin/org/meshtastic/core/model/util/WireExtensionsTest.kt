/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.model.util

import co.touchlab.kermit.Logger
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.Position
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User

/**
 * Unit tests for Wire extension functions.
 *
 * Tests safe decoding, size validation, and JSON marshalling extensions to ensure proper error handling and
 * functionality.
 */
class WireExtensionsTest {

    private val testLogger = Logger

    @Before
    fun setUp() {
        // Setup test logger if needed
    }

    // ===== decodeOrNull() Tests =====

    @Test
    fun `decodeOrNull with valid ByteString returns decoded message`() {
        // Arrange
        val position = Position(latitude_i = 371234567, longitude_i = -1220987654, altitude = 15)
        val encoded = Position.ADAPTER.encode(position)
        val byteString = encoded.toByteString()

        // Act
        val decoded = Position.ADAPTER.decodeOrNull(byteString, testLogger)

        // Assert
        assertNotNull(decoded)
        assertEquals(position.latitude_i, decoded!!.latitude_i)
        assertEquals(position.longitude_i, decoded.longitude_i)
        assertEquals(position.altitude, decoded.altitude)
    }

    @Test
    fun `decodeOrNull with null ByteString returns null`() {
        // Act
        val result = Position.ADAPTER.decodeOrNull(null as ByteString?, testLogger)

        // Assert
        assertNull(result)
    }

    @Test
    fun `decodeOrNull with empty ByteString returns empty message`() {
        // Act
        val result = Position.ADAPTER.decodeOrNull(ByteString.EMPTY, testLogger)

        // Assert
        assertNotNull(result)
        // An empty position should have null/default values
        assertNull(result!!.latitude_i)
    }

    @Test
    fun `decodeOrNull with valid ByteArray returns decoded message`() {
        // Arrange
        val position = Position(latitude_i = 371234567, longitude_i = -1220987654)
        val encoded = Position.ADAPTER.encode(position)

        // Act
        val decoded = Position.ADAPTER.decodeOrNull(encoded, testLogger)

        // Assert
        assertNotNull(decoded)
        assertEquals(position.latitude_i, decoded!!.latitude_i)
        assertEquals(position.longitude_i, decoded.longitude_i)
    }

    @Test
    fun `decodeOrNull with null ByteArray returns null`() {
        // Act
        val result = Position.ADAPTER.decodeOrNull(null as ByteArray?, testLogger)

        // Assert
        assertNull(result)
    }

    @Test
    fun `decodeOrNull with empty ByteArray returns empty message`() {
        // Act
        val result = Position.ADAPTER.decodeOrNull(ByteArray(0), testLogger)

        // Assert
        assertNotNull(result)
        assertNull(result!!.latitude_i)
    }

    @Test
    fun `decodeOrNull with invalid data returns null`() {
        // Arrange
        // A single byte 0xFF is an invalid field tag (field 0 is reserved and tags are varints)
        val invalidBytes = ByteString.of(0xFF.toByte())

        // Act - should not throw, should return null
        val result = Position.ADAPTER.decodeOrNull(invalidBytes, testLogger)

        // Assert
        assertNull(result)
    }

    // ===== Size Validation Tests =====

    @Test
    fun `isWithinSizeLimit returns true for message under limit`() {
        // Arrange
        val position = Position(latitude_i = 371234567)
        val limit = 1000

        // Act
        val isValid = Position.ADAPTER.isWithinSizeLimit(position, limit)

        // Assert
        assertTrue(isValid)
    }

    @Test
    fun `isWithinSizeLimit returns false for message over limit`() {
        // Arrange
        val telemetry =
            Telemetry(
                device_metrics =
                DeviceMetrics(voltage = 4.2f, battery_level = 85, air_util_tx = 5.0f, channel_utilization = 15.0f),
            )
        val limit = 1 // Artificially low limit

        // Act
        val isValid = Telemetry.ADAPTER.isWithinSizeLimit(telemetry, limit)

        // Assert
        assertEquals(false, isValid)
    }

    @Test
    fun `sizeInBytes returns accurate encoded size`() {
        // Arrange
        val position = Position(latitude_i = 371234567, longitude_i = -1220987654)

        // Act
        val size = Position.ADAPTER.sizeInBytes(position)
        val actualEncoded = Position.ADAPTER.encode(position)

        // Assert
        assertEquals(actualEncoded.size, size)
        assertTrue(size > 0)
    }

    @Test
    fun `sizeInBytes for empty message`() {
        // Arrange
        val emptyPosition = Position()

        // Act
        val size = Position.ADAPTER.sizeInBytes(emptyPosition)

        // Assert
        assertTrue(size >= 0)
    }

    @Test
    fun `sizeInBytes matches wire encoding size`() {
        // Arrange
        val user = User(id = "12345", long_name = "Test User", short_name = "TU")

        // Act
        val extensionSize = User.ADAPTER.sizeInBytes(user)
        val actualEncoded = User.ADAPTER.encode(user)

        // Assert
        assertEquals(extensionSize, actualEncoded.size)
    }

    // ===== JSON Marshalling Tests =====

    @Test
    fun `toReadableString returns non-empty string`() {
        // Arrange
        val position = Position(latitude_i = 371234567, longitude_i = -1220987654)

        // Act
        val readable = Position.ADAPTER.toReadableString(position)

        // Assert
        assertNotNull(readable)
        assertTrue(readable.isNotEmpty())
        assertTrue(readable.contains("Position"))
    }

    @Test
    fun `toReadableString contains field values`() {
        // Arrange
        val position = Position(latitude_i = 12345, longitude_i = 67890)

        // Act
        val readable = Position.ADAPTER.toReadableString(position)

        // Assert
        assertTrue(readable.contains("12345"))
        assertTrue(readable.contains("67890"))
    }

    @Test
    fun `toOneLiner returns single line string`() {
        // Arrange
        val telemetry = Telemetry(device_metrics = DeviceMetrics(voltage = 4.2f))

        // Act
        val oneLiner = Telemetry.ADAPTER.toOneLiner(telemetry)

        // Assert
        assertNotNull(oneLiner)
        assertEquals(false, oneLiner.contains("\n"))
        assertTrue(oneLiner.isNotEmpty())
    }

    @Test
    fun `toOneLiner contains essential data`() {
        // Arrange
        val user = User(long_name = "Test User")

        // Act
        val oneLiner = User.ADAPTER.toOneLiner(user)

        // Assert
        assertTrue(oneLiner.contains("Test User"))
    }

    // ===== Integration Tests =====

    @Test
    fun `decode and encode roundtrip maintains data`() {
        // Arrange
        val originalPosition =
            Position(latitude_i = 371234567, longitude_i = -1220987654, altitude = 15, precision_bits = 5)
        val encoded = Position.ADAPTER.encode(originalPosition)

        // Act
        val decoded = Position.ADAPTER.decodeOrNull(encoded, testLogger)

        // Assert
        assertNotNull(decoded)
        assertEquals(originalPosition.latitude_i, decoded!!.latitude_i)
        assertEquals(originalPosition.longitude_i, decoded.longitude_i)
        assertEquals(originalPosition.altitude, decoded.altitude)
        assertEquals(originalPosition.precision_bits, decoded.precision_bits)
    }

    @Test
    fun `size checking prevents oversized messages`() {
        // Arrange
        val position = Position(latitude_i = 123456789, longitude_i = 987654321, altitude = 100)
        val maxSize = 5 // Very small limit

        // Act
        val isValid = Position.ADAPTER.isWithinSizeLimit(position, maxSize)
        val actualSize = Position.ADAPTER.sizeInBytes(position)

        // Assert
        assertEquals(false, isValid)
        assertTrue(actualSize > maxSize)
    }

    @Test
    fun `multiple messages with different sizes`() {
        // Arrange
        val smallUser = User(short_name = "A")
        val largeUser = User(long_name = "Very Long Name " + "X".repeat(100))

        // Act
        val smallSize = User.ADAPTER.sizeInBytes(smallUser)
        val largeSize = User.ADAPTER.sizeInBytes(largeUser)

        // Assert
        assertTrue(smallSize < largeSize)
        assertTrue(largeSize > smallSize)
    }

    @Test
    fun `readable string format consistency`() {
        // Arrange
        val position = Position(latitude_i = 123456)

        // Act
        val readable1 = Position.ADAPTER.toReadableString(position)
        val readable2 = Position.ADAPTER.toReadableString(position)

        // Assert
        assertEquals(readable1, readable2)
    }

    @Test
    fun `oneLiner format consistency`() {
        // Arrange
        val user = User(long_name = "Test")

        // Act
        val line1 = User.ADAPTER.toOneLiner(user)
        val line2 = User.ADAPTER.toOneLiner(user)

        // Assert
        assertEquals(line1, line2)
        assertEquals(false, line1.contains("\n"))
    }
}
