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

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.testing.FakeMeshLogPrefs
import org.meshtastic.core.testing.FakeMeshLogRepository
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SetMeshLogSettingsUseCaseTest {

    private lateinit var meshLogRepository: FakeMeshLogRepository
    private lateinit var meshLogPrefs: FakeMeshLogPrefs
    private lateinit var useCase: SetMeshLogSettingsUseCase

    @BeforeTest
    fun setUp() {
        meshLogRepository = FakeMeshLogRepository()
        meshLogPrefs = FakeMeshLogPrefs()
        useCase = SetMeshLogSettingsUseCase(meshLogRepository, meshLogPrefs)
    }

    @Test
    fun `setRetentionDays clamps value and deletes old logs`() = runTest {
        useCase.setRetentionDays(500) // Max is 365
        assertEquals(365, meshLogPrefs.retentionDays.value)
        assertEquals(365, meshLogRepository.lastDeletedOlderThan)
    }

    @Test
    fun `setLoggingEnabled false deletes all logs`() = runTest {
        useCase.setLoggingEnabled(false)
        assertEquals(false, meshLogPrefs.loggingEnabled.value)
        assertEquals(true, meshLogRepository.deleteAllCalled)
    }

    @Test
    fun `setLoggingEnabled true deletes logs older than retention`() = runTest {
        meshLogPrefs.setRetentionDays(15)
        useCase.setLoggingEnabled(true)
        assertEquals(true, meshLogPrefs.loggingEnabled.value)
        assertEquals(15, meshLogRepository.lastDeletedOlderThan)
    }
}
