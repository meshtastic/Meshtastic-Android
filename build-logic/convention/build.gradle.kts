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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

group = "org.meshtastic.buildlogic"

// Configure the build-logic plugins to target JDK 17
// This improves compatibility for developers building the project or consuming its libraries.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // This allows the use of the 'libs' type-safe accessor in the Kotlin source of the plugins
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    compileOnly(libs.android.gradleApiPlugin)
    compileOnly(libs.serialization.gradlePlugin)
    compileOnly(libs.android.tools.common)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.multiplatform.gradlePlugin)
    compileOnly(libs.datadog.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.dokka.gradlePlugin)
    compileOnly(libs.firebase.crashlytics.gradlePlugin)
    compileOnly(libs.google.services.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    implementation(libs.kover.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.androidx.room.gradlePlugin)
    compileOnly(libs.secrets.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
    compileOnly(libs.test.retry.gradlePlugin)
    compileOnly("com.dropbox.dependency-guard:dependency-guard:0.5.0")
    compileOnly(libs.truth)

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
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(rootProject.file("../config/spotless/.editorconfig").path)
        licenseHeaderFile(rootProject.file("../config/spotless/copyright.kt"))
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(rootProject.file("../config/spotless/.editorconfig").path)
        licenseHeaderFile(
            rootProject.file("../config/spotless/copyright.kts"),
            "(^(?![\\/ ]\\*).*$)"
        )
    }
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(rootProject.file("../config/detekt/detekt.yml"))
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

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "meshtastic.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidFlavors") {
            id = "meshtastic.android.application.flavors"
            implementationClass = "AndroidApplicationFlavorsConventionPlugin"
        }
        register("androidLibrary") {
            id = "meshtastic.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLint") {
            id = "meshtastic.android.lint"
            implementationClass = "AndroidLintConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "meshtastic.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "meshtastic.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("kotlinXSerialization") {
            id = "meshtastic.kotlinx.serialization"
            implementationClass = "KotlinXSerializationConventionPlugin"
        }
        register("meshtasticAnalytics") {
            id = "meshtastic.analytics"
            implementationClass = "AnalyticsConventionPlugin"
        }
        register("meshtasticHilt") {
            id = "meshtastic.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("meshtasticDetekt") {
            id = "meshtastic.detekt"
            implementationClass = "DetektConventionPlugin"
        }
        register("androidRoom") {
            id = "meshtastic.android.room"
            implementationClass = "AndroidRoomConventionPlugin"
        }

        register("meshtasticSpotless") {
            id = "meshtastic.spotless"
            implementationClass = "SpotlessConventionPlugin"
        }

        register("kmpLibrary") {
            id = "meshtastic.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }

        register("kmpLibraryCompose") {
            id = "meshtastic.kmp.library.compose"
            implementationClass = "KmpLibraryComposeConventionPlugin"
        }
        
        register("dokka") {
            id = "meshtastic.dokka"
            implementationClass = "DokkaConventionPlugin"
        }
        
        register("kover") {
            id = "meshtastic.kover"
            implementationClass = "KoverConventionPlugin"
        }

        register("root") {
            id = "meshtastic.root"
            implementationClass = "RootConventionPlugin"
        }

    }
}
