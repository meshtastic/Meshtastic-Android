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
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.meshtastic.buildlogic.configureFlavors

/**
 * Flavor configuration for Android applications.
 *
 * Optimization note: This is nearly identical to AndroidLibraryFlavorsConventionPlugin. The underlying
 * configureFlavors() function already handles both ApplicationExtension and LibraryExtension. Could be consolidated
 * into a single plugin accepting CommonExtension, but kept separate for now to maintain explicit intent in
 * build.gradle.kts declarations.
 */
class AndroidApplicationFlavorsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) { extensions.configure<ApplicationExtension> { configureFlavors(this) } }
    }
}
