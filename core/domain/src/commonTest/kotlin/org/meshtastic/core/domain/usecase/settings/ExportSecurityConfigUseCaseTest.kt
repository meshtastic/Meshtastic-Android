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
package org.meshtastic.core.domain.usecase.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportSecurityConfigUseCaseTest {

    private lateinit var useCase: ExportSecurityConfigUseCase

    @BeforeTest
    fun setUp() {
        useCase = ExportSecurityConfigUseCase()
    }

    @Test
    fun `invoke writes valid JSON to output stream`() {
        // Arrange
        val publicKey = byteArrayOf(1, 2, 3).toByteString()
        val privateKey = byteArrayOf(4, 5, 6).toByteString()
        val config = Config.SecurityConfig(public_key = publicKey, private_key = privateKey)
        val buffer = Buffer()

        // Act
        val result = useCase(buffer, config)

        // Assert
        assertTrue(result.isSuccess)
        val json = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
        assertTrue(json.containsKey("timestamp"))
        assertTrue(json.containsKey("public_key"))
        assertTrue(json.containsKey("private_key"))
        // Check base64 values
        assertEquals("AQID", json["public_key"]?.jsonPrimitive?.content)
        assertEquals("BAUG", json["private_key"]?.jsonPrimitive?.content)
    }
}
