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

import org.meshtastic.buildlogic.GenerateSupportedLocalesTask

plugins {
    alias(libs.plugins.meshtastic.kmp.library)
    alias(libs.plugins.meshtastic.kmp.library.compose)
}

val generateSupportedLocales =
    tasks.register<GenerateSupportedLocalesTask>("generateSupportedLocales") {
        description = "Generates supportedLocaleTags from the values-* directories Crowdin writes."
        resourcesDir.set(layout.projectDirectory.dir("src/commonMain/composeResources"))
        outputDir.set(layout.buildDirectory.dir("generated/supportedLocales/commonMain/kotlin"))
    }

kotlin {
    android {
        androidResources {
            enable = true
            resourcePrefix = "meshtastic_"
        }
        withHostTest { isIncludeAndroidResources = true }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateSupportedLocales.flatMap { it.outputDir })
            dependencies { implementation(projects.core.common) }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "org.meshtastic.core.resources"
}
