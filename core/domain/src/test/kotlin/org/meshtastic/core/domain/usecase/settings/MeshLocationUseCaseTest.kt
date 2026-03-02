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

import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.model.RadioController

class MeshLocationUseCaseTest {

    private lateinit var radioController: RadioController
    private lateinit var useCase: MeshLocationUseCase

    @Before
    fun setUp() {
        radioController = mockk(relaxed = true)
        useCase = MeshLocationUseCase(radioController)
    }

    @Test
    fun `startProvidingLocation calls radioController`() {
        useCase.startProvidingLocation()
        verify { radioController.startProvideLocation() }
    }

    @Test
    fun `stopProvidingLocation calls radioController`() {
        useCase.stopProvidingLocation()
        verify { radioController.stopProvideLocation() }
    }
}
