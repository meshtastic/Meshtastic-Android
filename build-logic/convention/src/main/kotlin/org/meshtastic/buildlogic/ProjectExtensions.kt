/*
 * Copyright (c) 2025 Meshtastic LLC
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
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import org.gradle.plugin.use.PluginDependency
import java.io.FileInputStream
import java.util.Properties

val Project.libs
    get(): VersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).get()

fun VersionCatalog.bundle(alias: String): Provider<ExternalModuleDependencyBundle> =
    findBundle(alias).get()

fun VersionCatalog.plugin(alias: String): Provider<PluginDependency> =
    findPlugin(alias).get()

fun VersionCatalog.version(alias: String): String =
    findVersion(alias).get().requiredVersion

val Project.configProperties: Properties
    get() {
        val properties = Properties()
        val propertiesFile = rootProject.file("config.properties")
        if (propertiesFile.exists()) {
            FileInputStream(propertiesFile).use { properties.load(it) }
        }
        return properties
    }
