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

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

internal fun Project.configureDetekt(extension: DetektExtension) {
    extension.apply {
        val detektVersion: String = this@configureDetekt.libs.findVersion("detekt").get().requiredVersion
        toolVersion = detektVersion
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        allRules = false
        baseline = file("detekt-baseline.xml")
        source.setFrom(
            files(
                "src/main/java",
                "src/main/kotlin",
            )
        )
    }

    tasks.withType<Detekt> {
        reports {
            sarif.required.set(true)
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
        }
    }

    dependencies {
        add("detektPlugins", this@configureDetekt.libs.findLibrary("detekt.compose").get())
    }
}
