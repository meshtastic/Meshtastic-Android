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

/**
 * Adds exactly one maplibre-compose native runtime — the one matching this build host — as `runtimeOnly`.
 *
 * Each runtime artifact carries that platform's maplibre-native blob, so adding them all would bloat every
 * distribution with four unusable copies. Upstream publishes no macos-x64 artifact, which matches our release matrix.
 * Shared by `:desktopApp` and `:marketing-screenshots`, the two JVM modules that draw a MapLibre map.
 */
fun Project.maplibreDesktopRuntime() {
    val osName = providers.systemProperty("os.name").get().lowercase()
    val osArch = providers.systemProperty("os.arch").get().lowercase()
    val isArm = osArch.contains("aarch64") || osArch.contains("arm64")
    val alias =
        when {
            osName.contains("mac") -> "maplibre-compose-runtime-metal-macos-arm64"
            osName.contains("win") && isArm -> "maplibre-compose-runtime-vulkan-windows-arm64"
            osName.contains("win") -> "maplibre-compose-runtime-vulkan-windows-x64"
            isArm -> "maplibre-compose-runtime-vulkan-linux-arm64"
            else -> "maplibre-compose-runtime-vulkan-linux-x64"
        }
    dependencies.add("runtimeOnly", libs.library(alias))
}
