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
package org.meshtastic.core.ble.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

/**
 * wasmJs DI module, mirroring `nonWebMain`'s `di/CoreBleModule.kt` shape.
 *
 * Koin annotation processing in this repo is the compiler-plugin-based Koin K2 plugin (see
 * `build-logic/convention/src/main/kotlin/KoinConventionPlugin.kt`), not KSP — it applies uniformly to every Kotlin
 * target via the `org.jetbrains.kotlin.multiplatform` plugin hook, so no per-target wiring is needed for
 * `@ComponentScan` to pick up [WebBleConnectionFactory][org.meshtastic.core.ble.WebBleConnectionFactory],
 * [WebBleScanner][org.meshtastic.core.ble.WebBleScanner], and
 * [WebBluetoothRepository][org.meshtastic.core.ble.WebBluetoothRepository]'s `@Single` annotations on the wasmJs
 * compilation.
 *
 * Unlike `CoreBleModule`, this module provides no `BleLoggingConfig` — that type stayed in `nonWebMain` along with the
 * Kable logging it configures, and nothing in `wasmJsMain` depends on it.
 */
@Module
@ComponentScan("org.meshtastic.core.ble")
class CoreBleWasmJsModule
