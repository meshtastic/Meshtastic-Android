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

import org.meshtastic.core.testing.FakeRadioController
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class MeshLocationUseCaseTest {

    private lateinit var radioController: FakeRadioController
    private lateinit var useCase: MeshLocationUseCase

    @BeforeTest
    fun setUp() {
        radioController = FakeRadioController()
        useCase = MeshLocationUseCase(radioController)
    }

    @Test
    fun `startProvidingLocation calls radioController`() {
        useCase.startProvidingLocation()
        assertTrue(radioController.startProvideLocationCalled)
    }

    @Test
    fun `stopProvidingLocation calls radioController`() {
        useCase.stopProvidingLocation()
        assertTrue(radioController.stopProvideLocationCalled)
    }
}
