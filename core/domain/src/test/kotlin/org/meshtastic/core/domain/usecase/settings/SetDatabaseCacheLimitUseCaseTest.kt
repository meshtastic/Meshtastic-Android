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

import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.database.DatabaseConstants

class SetDatabaseCacheLimitUseCaseTest {

    private lateinit var databaseManager: DatabaseManager
    private lateinit var useCase: SetDatabaseCacheLimitUseCase

    @Before
    fun setUp() {
        databaseManager = mockk(relaxed = true)
        useCase = SetDatabaseCacheLimitUseCase(databaseManager)
    }

    @Test
    fun `invoke calls setCacheLimit with clamped value`() {
        // Act & Assert
        useCase(0)
        verify { databaseManager.setCacheLimit(DatabaseConstants.MIN_CACHE_LIMIT) }

        useCase(100)
        verify { databaseManager.setCacheLimit(DatabaseConstants.MAX_CACHE_LIMIT) }

        useCase(5)
        verify { databaseManager.setCacheLimit(5) }
    }
}
