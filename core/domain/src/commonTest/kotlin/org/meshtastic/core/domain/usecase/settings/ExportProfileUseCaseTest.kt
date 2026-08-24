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

import okio.Buffer
import org.meshtastic.proto.DeviceProfile
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class ExportProfileUseCaseTest {

    private lateinit var useCase: ExportProfileUseCase

    @BeforeTest
    fun setUp() {
        useCase = ExportProfileUseCase()
    }

    @Test
    fun `invoke writes encoded profile to output stream`() {
        // Arrange
        val profile = DeviceProfile(long_name = "Export Node")
        val buffer = Buffer()

        // Act
        val result = useCase(buffer, profile)

        // Assert
        assertTrue(result.isSuccess)
        assertContentEquals(profile.encode(), buffer.readByteArray())
    }
}
