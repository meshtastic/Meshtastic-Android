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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.configuration.BuildFeatures
import org.gradle.kotlin.dsl.apply
import org.meshtastic.buildlogic.configureKmpWebTarget
import org.meshtastic.buildlogic.requireIsolatedProjectsOffForWeb
import javax.inject.Inject

/**
 * A KMP library `:webApp` reaches. Registers the wasmJs target only under `-Pmeshtastic.web=true`, and always builds
 * the `nonWebMain`/`nonWebTest` source sets for code that has no browser equivalent.
 */
abstract class KmpWebConventionPlugin @Inject constructor(private val buildFeatures: BuildFeatures) : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            requireIsolatedProjectsOffForWeb(buildFeatures)
            apply(plugin = "meshtastic.kmp.library")
            configureKmpWebTarget()
        }
    }
}
