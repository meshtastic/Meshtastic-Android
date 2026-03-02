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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meshtastic.proto.DeviceProfile
import java.io.ByteArrayOutputStream

class ExportProfileUseCaseTest {

    private lateinit var useCase: ExportProfileUseCase

    @Before
    fun setUp() {
        useCase = ExportProfileUseCase()
    }

    @Test
    fun `invoke writes encoded profile to output stream`() {
        // Arrange
        val profile = DeviceProfile(long_name = "Export Node")
        val outputStream = ByteArrayOutputStream()

        // Act
        val result = useCase(outputStream, profile)

        // Assert
        assertTrue(result.isSuccess)
        assertArrayEquals(profile.encode(), outputStream.toByteArray())
    }
}
