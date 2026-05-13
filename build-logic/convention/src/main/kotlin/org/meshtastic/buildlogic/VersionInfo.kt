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
 * Shared version metadata resolved from config.properties, environment variables, Gradle properties, and git commit
 * count. Used by both the Android app and Desktop host shells to avoid duplicating resolution logic.
 */
data class VersionInfo(
    val versionCode: Int,
    val versionName: String,
    val minFwVersion: String,
    val absMinFwVersion: String,
)

/**
 * Resolves version information using the following precedence:
 * 1. Gradle properties injected by the IDE or CI (`android.injected.version.code`, etc.)
 * 2. Environment variables (`VERSION_CODE`, `VERSION_NAME`)
 * 3. Git commit count + offset from `config.properties`
 * 4. Fallback defaults
 */
fun Project.resolveVersionInfo(): VersionInfo {
    val gitVersionProvider = providers.of(GitVersionValueSource::class.java) {}
    val vcOffset = configProperties.getProperty("VERSION_CODE_OFFSET")?.toInt() ?: 0

    val versionCode =
        findProperty("android.injected.version.code")?.toString()?.toInt()
            ?: System.getenv("VERSION_CODE")?.toInt()
            ?: (gitVersionProvider.get().toInt() + vcOffset)

    val versionName =
        findProperty("android.injected.version.name")?.toString()
            ?: findProperty("appVersionName")?.toString()
            ?: System.getenv("VERSION_NAME")
            ?: configProperties.getProperty("VERSION_NAME_BASE")
            ?: "1.0.0"

    val minFwVersion = configProperties.getProperty("MIN_FW_VERSION") ?: ""
    val absMinFwVersion = configProperties.getProperty("ABS_MIN_FW_VERSION") ?: ""

    return VersionInfo(
        versionCode = versionCode,
        versionName = versionName,
        minFwVersion = minFwVersion,
        absMinFwVersion = absMinFwVersion,
    )
}
