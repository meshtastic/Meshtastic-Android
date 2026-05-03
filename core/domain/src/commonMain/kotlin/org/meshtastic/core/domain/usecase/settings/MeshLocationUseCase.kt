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

import org.koin.core.annotation.Single
import org.meshtastic.core.model.RadioController

/** Use case for controlling location sharing with the mesh. */
@Single
open class MeshLocationUseCase constructor(private val radioController: RadioController) {
    /** Starts providing the phone's location to the mesh. */
    fun startProvidingLocation() {
        radioController.startProvideLocation()
    }

    /** Stops providing the phone's location to the mesh. */
    fun stopProvidingLocation() {
        radioController.stopProvideLocation()
    }
}
