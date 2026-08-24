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
package org.meshtastic.core.data.di

import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.common.di.PROCESS_LIFECYCLE
import org.meshtastic.core.model.util.MeshDataMapper
import org.meshtastic.core.model.util.NodeIdLookup
import org.meshtastic.core.repository.ActiveConversationTracker
import kotlin.time.Clock

@Module
@ComponentScan("org.meshtastic.core.data")
class CoreDataModule {
    @Single fun provideMeshDataMapper(nodeIdLookup: NodeIdLookup): MeshDataMapper = MeshDataMapper(nodeIdLookup)

    @Single fun provideClock(): Clock = Clock.System

    /**
     * [ActiveConversationTracker] is a plain holder so it stays trivially constructible in tests; the process lifecycle
     * is bridged into it here, where an application-lifetime scope is already available.
     */
    @Single
    fun provideActiveConversationTracker(
        @Provided @Named(PROCESS_LIFECYCLE) processLifecycle: Lifecycle,
        @Provided applicationScope: ApplicationCoroutineScope,
    ): ActiveConversationTracker = ActiveConversationTracker().also { tracker ->
        applicationScope.launch {
            processLifecycle.currentStateFlow.collect { state ->
                tracker.setAppForeground(state.isAtLeast(Lifecycle.State.STARTED))
            }
        }
    }
}
