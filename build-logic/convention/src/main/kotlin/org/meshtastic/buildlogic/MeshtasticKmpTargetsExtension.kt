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
package org.meshtastic.buildlogic

import org.gradle.api.provider.Property

/**
 * Per-module opt-in for the `wasmJs` Kotlin target, configured from a module's own
 * `build.gradle.kts` as `meshtasticKmpTargets { web.set(true) }`.
 *
 * `wasmJs` is additive-only: a module that never sets [web] is completely unaffected — no new
 * target, no new tasks. A module cannot simply flip [web] on if it has a native-only dependency
 * (Kable, `androidx.sqlite.bundled`, ...) declared directly in `commonMain`; such a module must
 * also set [hoistNativeOnlyDependencies] and move that dependency into the `nativeMain`
 * intermediate source set this extension creates on its behalf (see `configureWasmJsTarget` in
 * `KotlinAndroid.kt`).
 */
abstract class MeshtasticKmpTargetsExtension {
    abstract val web: Property<Boolean>
    abstract val hoistNativeOnlyDependencies: Property<Boolean>
}
