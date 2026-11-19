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

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec

/**
 * Stops the Kotlin/Wasm tooling plugins adding their own download repositories, which `FAIL_ON_PROJECT_REPOS` rejects.
 * Node.js, Yarn and Binaryen resolve through the ivy repositories `settings.gradle.kts` declares instead.
 */
fun Project.configureWasmToolingDownloads() {
    plugins.withType<WasmNodeJsPlugin> {
        extensions.configure<WasmNodeJsEnvSpec> { downloadBaseUrl.set(null as String?) }
    }
    plugins.withType<WasmYarnPlugin> {
        extensions.configure<WasmYarnRootEnvSpec> { downloadBaseUrl.set(null as String?) }
    }
    plugins.withType<BinaryenPlugin> {
        extensions.configure<BinaryenEnvSpec> { downloadBaseUrl.set(null as String?) }
    }
}
