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

import dev.mokkery.answering.returns

import dev.mokkery.mock
import dev.mokkery.verify
import org.meshtastic.core.datastore.UiPreferencesDataSource
import kotlin.test.BeforeTest
import kotlin.test.Test

class SetThemeUseCaseTest {

    private lateinit var uiPreferencesDataSource: UiPreferencesDataSource
    private lateinit var useCase: SetThemeUseCase

    @BeforeTest
    fun setUp() {
        uiPreferencesDataSource = mock(dev.mokkery.MockMode.autofill)
        useCase = SetThemeUseCase(uiPreferencesDataSource)
    }

    @Test
    fun `invoke calls setTheme on data source`() {
        // Act
        useCase(1)

        // Assert
        verify { uiPreferencesDataSource.setTheme(1) }
    }
}
