/*
 * Copyright (c) 2025 Meshtastic LLC
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

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.prefs.homoglyph.HomoglyphPrefs

class ToggleHomoglyphEncodingUseCaseTest {

    private lateinit var homoglyphEncodingPrefs: HomoglyphPrefs
    private lateinit var useCase: ToggleHomoglyphEncodingUseCase

    @Before
    fun setUp() {
        homoglyphEncodingPrefs = mockk(relaxed = true)
        useCase = ToggleHomoglyphEncodingUseCase(homoglyphEncodingPrefs)
    }

    @Test
    fun `invoke toggles homoglyph encoding from false to true`() {
        // Arrange
        every { homoglyphEncodingPrefs.homoglyphEncodingEnabled } returns false

        // Act
        useCase()

        // Assert
        verify { homoglyphEncodingPrefs.homoglyphEncodingEnabled = true }
    }

    @Test
    fun `invoke toggles homoglyph encoding from true to false`() {
        // Arrange
        every { homoglyphEncodingPrefs.homoglyphEncodingEnabled } returns true

        // Act
        useCase()

        // Assert
        verify { homoglyphEncodingPrefs.homoglyphEncodingEnabled = false }
    }
}
