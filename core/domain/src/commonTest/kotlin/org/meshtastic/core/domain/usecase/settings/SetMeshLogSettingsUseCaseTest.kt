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

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.MeshLogPrefs
import org.meshtastic.core.repository.MeshLogRepository
import kotlin.test.BeforeTest
import kotlin.test.Test

class SetMeshLogSettingsUseCaseTest {

    private lateinit var meshLogRepository: MeshLogRepository
    private lateinit var meshLogPrefs: MeshLogPrefs
    private lateinit var useCase: SetMeshLogSettingsUseCase

    @BeforeTest
    fun setUp() {
        meshLogRepository = mockk(relaxed = true)
        meshLogPrefs = mockk(relaxed = true)
        useCase = SetMeshLogSettingsUseCase(meshLogRepository, meshLogPrefs)
    }

    @Test
    fun `setRetentionDays clamps and updates prefs and repository`() = runTest {
        // Act
        useCase.setRetentionDays(MeshLogPrefs.MIN_RETENTION_DAYS - 1)

        // Assert
        verify { meshLogPrefs.setRetentionDays(MeshLogPrefs.MIN_RETENTION_DAYS) }
        coVerify { meshLogRepository.deleteLogsOlderThan(MeshLogPrefs.MIN_RETENTION_DAYS) }
    }

    @Test
    fun `setLoggingEnabled true triggers cleanup`() = runTest {
        // Arrange
        every { meshLogPrefs.retentionDays.value } returns 30

        // Act
        useCase.setLoggingEnabled(true)

        // Assert
        verify { meshLogPrefs.setLoggingEnabled(true) }
        coVerify { meshLogRepository.deleteLogsOlderThan(30) }
    }

    @Test
    fun `setLoggingEnabled false triggers deletion`() = runTest {
        // Act
        useCase.setLoggingEnabled(false)

        // Assert
        verify { meshLogPrefs.setLoggingEnabled(false) }
        coVerify { meshLogRepository.deleteAll() }
    }
}
