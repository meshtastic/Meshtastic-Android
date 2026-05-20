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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

group = "org.meshtastic.flatpak"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }

dependencies {
    // Allows type-safe accessors for libs in plugin build script
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    detektPlugins(libs.detekt.formatting)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

spotless {
    ratchetFrom("origin/main")
    kotlin {
        target("src/*/kotlin/**/*.kt", "src/*/java/**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(rootProject.file("../config/spotless/.editorconfig").path)
        licenseHeaderFile(rootProject.file("../config/spotless/copyright.kt"))
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath(rootProject.file("../config/spotless/.editorconfig").path)
        licenseHeaderFile(rootProject.file("../config/spotless/copyright.kts"), "(^(?![\\/ ]\\*).*$)")
    }
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(rootProject.file("../config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    baseline = file("detekt-baseline.xml")
    source.setFrom(files("src/main/java", "src/main/kotlin"))
}

gradlePlugin {
    plugins {
        register("meshtasticFlatpak") {
            id = "meshtastic.flatpak"
            implementationClass = "FlatpakConventionPlugin"
        }
    }
}
