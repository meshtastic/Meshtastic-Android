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

import com.android.build.api.dsl.androidLibrary
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.FileInputStream
import java.util.Properties

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.multiplatform")
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")
            apply(plugin = "com.android.kotlin.multiplatform.library")
            apply(plugin = "meshtastic.detekt")
            apply(plugin = "meshtastic.spotless")
            apply(plugin = "com.autonomousapps.dependency-analysis")

            val configPropertiesFile = rootProject.file("config.properties")
            val configProperties = Properties()

            if (configPropertiesFile.exists()) {
                FileInputStream(configPropertiesFile).use { configProperties.load(it) }
            }

            extensions.configure<KotlinMultiplatformExtension> {
                @Suppress("UnstableApiUsage")
                androidLibrary {
                    compileSdk = configProperties.getProperty("COMPILE_SDK").toInt()
                    minSdk = configProperties.getProperty("MIN_SDK").toInt()
                }
            }
        }
    }
}
