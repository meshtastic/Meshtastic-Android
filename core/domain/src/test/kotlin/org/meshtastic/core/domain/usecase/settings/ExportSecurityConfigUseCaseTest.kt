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
package org.meshtastic.core.domain.usecase.settings

import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.proto.Config
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class ExportSecurityConfigUseCaseTest {

    private lateinit var useCase: ExportSecurityConfigUseCase

    @Before
    fun setUp() {
        useCase = ExportSecurityConfigUseCase()
    }

    @Test
    fun `invoke writes valid JSON to output stream`() {
        // Arrange
        val publicKey = byteArrayOf(1, 2, 3).toByteString()
        val privateKey = byteArrayOf(4, 5, 6).toByteString()
        val config = Config.SecurityConfig(public_key = publicKey, private_key = privateKey)
        val outputStream = ByteArrayOutputStream()

        // Act
        val result = useCase(outputStream, config)

        // Assert
        assertTrue(result.isSuccess)
        val json = JSONObject(outputStream.toString())
        assertTrue(json.has("timestamp"))
        assertTrue(json.has("public_key"))
        assertTrue(json.has("private_key"))
        // Check base64 values
        assertEquals("AQID", json.getString("public_key"))
        assertEquals("BAUG", json.getString("private_key"))
    }
}
