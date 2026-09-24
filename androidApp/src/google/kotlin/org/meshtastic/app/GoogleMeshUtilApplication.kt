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
package org.meshtastic.app

import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.getKoin
import org.meshtastic.app.ai.appfunctions.AppFunctionStateSync

/** Google flavor Application subclass that starts the App Functions enabled-state sync. */
class GoogleMeshUtilApplication : MeshUtilApplication() {

    override fun onCreate() {
        super.onCreate()
        // Start the AppFunctions enabled-state sync. Resolved here (after startKoin has bound
        // androidContext) rather than via createdAtStart so that Koin graphs built outside a
        // running app — verification tests, previews — stay lazily constructible.
        // Off-main: construction forces the AppFunctionsPrefs subgraph and fires AppSearch binder calls.
        applicationScope.launch { getKoin().get<AppFunctionStateSync>() }
    }
}
