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
package org.meshtastic.feature.map.offline

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Application-scoped coordinator shared by map flavors and foreground lifecycle work. */
class BurningManPackRuntime private constructor(private val coordinatorFactory: () -> BurningManPackCoordinator) {
    val coordinator: BurningManPackCoordinator by lazy(coordinatorFactory)

    private val restoreMutex = Mutex()
    private var restored = false

    suspend fun restoreSelection(): SelectedBurningManPack? = restoreMutex.withLock {
        if (!restored) {
            coordinator.restoreValidatedSelection()
            restored = true
        }
        coordinator.selectedPack.value
    }

    companion object {
        private val instanceLock = Any()

        @Volatile private var instance: BurningManPackRuntime? = null
        private var factoryForTest: ((Context) -> BurningManPackCoordinator)? = null

        fun forContext(context: Context): BurningManPackRuntime {
            val applicationContext = context.applicationContext
            return instance
                ?: synchronized(instanceLock) {
                    instance
                        ?: run {
                            val coordinatorFactory = factoryForTest ?: ::newCoordinator
                            BurningManPackRuntime(coordinatorFactory = { coordinatorFactory(applicationContext) })
                                .also { instance = it }
                        }
                }
        }

        fun installFactoryForTest(factory: (Context) -> BurningManPackCoordinator) {
            synchronized(instanceLock) {
                check(instance == null) { "Reset BurningManPackRuntime before installing a test factory" }
                factoryForTest = factory
            }
        }

        fun resetForTest() {
            synchronized(instanceLock) {
                instance = null
                factoryForTest = null
            }
        }

        private fun newCoordinator(context: Context) = BurningManPackCoordinator(
            filesDirectory = context.filesDir,
            store = SharedPreferencesBurningManPackStore(context),
        )
    }
}
