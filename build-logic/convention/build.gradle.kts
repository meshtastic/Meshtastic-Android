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

group = "org.meshtastic.buildlogic"

// Configure the build-logic plugins to target JDK 21
// This improves compatibility for developers building the project or consuming its libraries.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }

dependencies {
    // This allows the use of the 'libs' type-safe accessor in the Kotlin source of the plugins
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    // Self-updating embedded Gradle Kotlin version
    val gradleKotlinVersion = KotlinVersion.CURRENT.toString()

    // ── Convention plugin compile dependencies ──────────────────────────────
    // These are standard compile-time dependencies used by our convention plugins.
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
    compileOnly(libs.koin.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
    // Mokkery needs `implementation` because convention plugins reference its types at compile time
    implementation(libs.mokkery.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.androidx.room.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
    compileOnly(libs.develocity.gradlePlugin)
    compileOnly(libs.aboutlibraries.gradlePlugin)

    detektPlugins(libs.detekt.formatting)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

// Isolated Projects forbids reaching into another project; ".." escapes the included build
// to the repo root, where the shared config lives.
val repoConfigDir = isolated.rootProject.projectDirectory.dir("../config")

spotless {
    ratchetFrom("origin/main")
    kotlin {
        target("src/*/kotlin/**/*.kt", "src/*/java/**/*.kt")
        // secrets_gradle_plugin is vendored third-party code (Apache-2.0) keeping Google's header.
        targetExclude("**/build/**/*.kt", "**/secrets_gradle_plugin/**")
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(repoConfigDir.file("spotless/.editorconfig").asFile.path)
        licenseHeaderFile(repoConfigDir.file("spotless/copyright.kt").asFile)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath(repoConfigDir.file("spotless/.editorconfig").asFile.path)
        licenseHeaderFile(repoConfigDir.file("spotless/copyright.kts").asFile, "(^(?![\\/ ]\\*).*$)")
    }
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(repoConfigDir.file("detekt/detekt.yml").asFile)
    buildUponDefaultConfig = true
    allRules = false
    baseline = file("detekt-baseline.xml")
    source.setFrom(files("src/main/java", "src/main/kotlin"))
}

// Vendored third-party code stays as close to upstream as possible — don't lint it to house style.
tasks.withType<dev.detekt.gradle.Detekt>().configureEach { exclude("**/secrets_gradle_plugin/**") }

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "meshtastic.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationFlavors") {
            id = "meshtastic.android.application.flavors"
            implementationClass = "AndroidApplicationFlavorsConventionPlugin"
        }
        register("androidLibrary") {
            id = "meshtastic.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryFlavors") {
            id = "meshtastic.android.library.flavors"
            implementationClass = "AndroidLibraryFlavorsConventionPlugin"
        }
        register("androidLint") {
            id = "meshtastic.android.lint"
            implementationClass = "AndroidLintConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "meshtastic.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidSecrets") {
            id = "meshtastic.android.secrets"
            implementationClass =
                "com.google.android.libraries.mapsplatform.secrets_gradle_plugin.SecretsPlugin"
        }
        register("androidScreenshot") {
            id = "meshtastic.android.screenshot"
            implementationClass = "AndroidScreenshotConventionPlugin"
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
        register("meshtasticKoin") {
            id = "meshtastic.koin"
            implementationClass = "KoinConventionPlugin"
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

        register("kmpJvmAndroid") {
            id = "meshtastic.kmp.jvm.android"
            implementationClass = "KmpJvmAndroidConventionPlugin"
        }

        register("kmpLibraryCompose") {
            id = "meshtastic.kmp.library.compose"
            implementationClass = "KmpLibraryComposeConventionPlugin"
        }

        register("kmpFeature") {
            id = "meshtastic.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
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

        register("docs") {
            id = "meshtastic.docs"
            implementationClass = "org.meshtastic.buildlogic.DocsTasks"
        }

        register("aboutLibraries") {
            id = "meshtastic.aboutlibraries"
            implementationClass = "AboutLibrariesConventionPlugin"
        }
    }
}
