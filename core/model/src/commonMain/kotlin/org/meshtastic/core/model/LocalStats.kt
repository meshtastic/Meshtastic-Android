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
package org.meshtastic.core.model

import org.meshtastic.proto.LocalStats

/**
 * Last measured noise floor in dBm, or null when this reading has never been reported ([LocalStats.noise_floor] still
 * holds firmware's `0` "no reading yet" sentinel). Every read of `noise_floor` should go through this so every consumer
 * treats absence the same way, rather than each site repeating its own `!= 0` / `?: 0` guard.
 */
val LocalStats.noiseFloorOrNull: Int?
    get() = noise_floor.takeIf { it != 0 }
