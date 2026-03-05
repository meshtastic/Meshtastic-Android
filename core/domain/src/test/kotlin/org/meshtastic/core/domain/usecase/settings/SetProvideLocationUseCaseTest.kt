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

import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.repository.UiPrefs

class SetProvideLocationUseCaseTest {

    private lateinit var uiPrefs: UiPrefs
    private lateinit var useCase: SetProvideLocationUseCase

    @Before
    fun setUp() {
        uiPrefs = mockk(relaxed = true)
        useCase = SetProvideLocationUseCase(uiPrefs)
    }

    @Test
    fun `invoke calls setShouldProvideNodeLocation on uiPrefs`() {
        // Act
        useCase(1234, true)

        // Assert
        verify { uiPrefs.setShouldProvideNodeLocation(1234, true) }
    }
}
