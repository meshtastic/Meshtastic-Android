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

import dev.mokkery.MockMode
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.UiPreferences
import kotlin.test.BeforeTest
import kotlin.test.Test

class SetProvideLocationUseCaseTest {

    private lateinit var uiPreferences: UiPreferences
    private lateinit var useCase: SetProvideLocationUseCase

    @BeforeTest
    fun setUp() {
        uiPreferences = mock(MockMode.autofill)
        useCase = SetProvideLocationUseCase(uiPreferences)
    }

    @Test
    fun `invoke calls setShouldProvideNodeLocation on uiPreferences`() = runTest {
        // Act
        useCase(123, true)

        // Assert
        verifySuspend { uiPreferences.setShouldProvideNodeLocation(123, true) }
    }
}
