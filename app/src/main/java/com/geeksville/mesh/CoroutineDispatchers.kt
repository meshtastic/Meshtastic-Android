/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh

import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Wrapper around `Dispatchers` to allow for easier testing when using dispatchers
 * in injected classes.
 */
class CoroutineDispatchers @Inject constructor() {
    val main = Dispatchers.Main
    val mainImmediate = Dispatchers.Main.immediate
    val default = Dispatchers.Default
    val io = Dispatchers.IO
}