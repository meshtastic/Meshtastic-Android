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
package org.meshtastic.core.di.di

import kotlinx.coroutines.Dispatchers
import org.meshtastic.core.common.util.ioDispatcher
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers

@Module
class CoreDiModule {
    @Single
    fun provideCoroutineDispatchers(): CoroutineDispatchers =
        CoroutineDispatchers(io = ioDispatcher, main = Dispatchers.Main, default = Dispatchers.Default)
}
