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
package org.meshtastic.core.common.di

import kotlinx.coroutines.CoroutineScope

/**
 * Process-lifetime scope owned by the mesh service, distinct from [ApplicationCoroutineScope] and from the DataStore
 * scope. Backed by a `SupervisorJob` so one failed child does not tear down the rest of the service.
 *
 * A type rather than a Koin qualifier so the compiler tells it apart from every other [CoroutineScope].
 */
interface ServiceScope : CoroutineScope

/** Presents an existing scope as [ServiceScope]; the wrapper adds nothing but identity. */
fun CoroutineScope.asServiceScope(): ServiceScope = object : ServiceScope, CoroutineScope by this {}
