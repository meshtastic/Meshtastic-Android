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

package com.geeksville.mesh.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named

internal fun Project.configureSpotless(extension: SpotlessExtension) = extension.apply {
    extension.apply {
        ratchetFrom("origin/main")
        kotlin {
            target("src/*/kotlin/**/*.kt", "src/*/java/**/*.kt")
            targetExclude("**/build/**/*.kt")
            ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
            ktlint("1.7.1").setEditorConfigPath(rootProject.file("config/spotless/.editorconfig").path)
            licenseHeaderFile(rootProject.file("config/spotless/copyright.kt"))
        }
        kotlinGradle {
            target("**/*.gradle.kts")
            ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
            ktlint("1.7.1").setEditorConfigPath(rootProject.file("config/spotless/.editorconfig").path)
            licenseHeaderFile(
                rootProject.file("config/spotless/copyright.kts"),
                "(^(?![\\/ ]\\*).*$)"
            )
        }
    }
}