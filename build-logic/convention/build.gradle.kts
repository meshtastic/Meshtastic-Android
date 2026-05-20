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

import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.flatpak.gradle.generator)
}

group = "org.meshtastic.buildlogic"

// Configure the build-logic plugins to target JDK 21
// This improves compatibility for developers building the project or consuming its libraries.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }

val flatpakOfflineDeps by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    // This allows the use of the 'libs' type-safe accessor in the Kotlin source of the plugins
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    // Version shortcuts for offline dependency declarations
    val kotlinVersion = libs.versions.kotlin.get()
    val koinPluginVersion = libs.versions.koin.plugin.get()
    // Self-updating embedded Gradle Kotlin version
    val gradleKotlinVersion = KotlinVersion.CURRENT.toString()

    /** Registers a dependency to be captured for the Flatpak offline repository. */
    fun flatpakDep(dependency: Any) {
        flatpakOfflineDeps(dependency)
    }

    /** Extracts a version-catalog plugin's marker coordinate and registers it for Flatpak offline. */
    fun flatpakPlugin(pluginProvider: Provider<org.gradle.plugin.use.PluginDependency>) {
        val plugin = pluginProvider.get()
        flatpakOfflineDeps("${plugin.pluginId}:${plugin.pluginId}.gradle.plugin:${plugin.version.requiredVersion}")
    }

    // ── Convention plugin compile dependencies ──────────────────────────────
    // These are standard compile-time dependencies. Since flatpakGradleGenerator includes
    // "compileClasspath", they are automatically captured along with all their transitives offline.
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
    compileOnly(libs.test.retry.gradlePlugin)
    compileOnly(libs.aboutlibraries.gradlePlugin)

    // ── Settings plugin marker artifacts (applied in settings.gradle.kts) ───
    flatpakDep("com.gradle.develocity:com.gradle.develocity.gradle.plugin:4.4.1")
    flatpakDep(
        "com.gradle.common-custom-user-data-gradle-plugin:" +
            "com.gradle.common-custom-user-data-gradle-plugin.gradle.plugin:2.6.0"
    )
    flatpakDep("org.gradle.toolchains.foojay-resolver:org.gradle.toolchains.foojay-resolver.gradle.plugin:1.0.0")

    // ── Dynamic plugin marker artifacts from Version Catalog ────────────────
    val versionCatalog = project.extensions.getByType<VersionCatalogsExtension>().named("libs")
    versionCatalog.pluginAliases.forEach { alias ->
        versionCatalog.findPlugin(alias).ifPresent { pluginProvider ->
            val plugin = pluginProvider.get()
            val version = plugin.version?.requiredVersion
            if (!version.isNullOrEmpty()) {
                flatpakOfflineDeps("${plugin.pluginId}:${plugin.pluginId}.gradle.plugin:$version")
            }
        }
    }

    // Other non-plugin dependencies needed explicitly for offline builds
    flatpakDep(libs.screenshot.validation.api)

    // ── Kotlin build tooling (resolved at task execution time by kotlin-dsl) ─
    flatpakDep("org.jetbrains.kotlin:kotlin-build-tools-compat:$gradleKotlinVersion")
    flatpakDep("org.jetbrains.kotlin:kotlin-build-tools-impl:$gradleKotlinVersion")
    flatpakDep("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:$gradleKotlinVersion")
    flatpakDep("org.jetbrains.kotlin:kotlin-sam-with-receiver-compiler-plugin-embeddable:$gradleKotlinVersion")
    flatpakDep("org.jetbrains.kotlin:kotlin-assignment-compiler-plugin-embeddable:$gradleKotlinVersion")
    flatpakDep("org.jetbrains.kotlin:kotlin-build-tools-compat:$kotlinVersion")
    flatpakDep("org.jetbrains.kotlin:kotlin-build-tools-impl:$kotlinVersion")
    flatpakDep("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:$kotlinVersion")

    // ── Compiler plugins resolved at task execution time ────────────────────
    flatpakDep("io.insert-koin:koin-compiler-plugin:$koinPluginVersion")
    flatpakDep("org.jetbrains.kotlin:kotlin-compose-compiler-plugin-embeddable:$kotlinVersion")
    flatpakDep("org.jetbrains.kotlin:kotlin-serialization-compiler-plugin-embeddable:$kotlinVersion")

    // ── Transitive deps not on standard classpaths ──────────────────────────
    flatpakDep("org.jetbrains.compose.material:material-ripple:1.11.0-beta03")
    flatpakDep("org.jetbrains.androidx.savedstate:savedstate-compose:1.3.6")
    flatpakDep("androidx.paging:paging-common:3.4.2")

    // ── Compose Desktop packaging (proguardReleaseJars task) ────────────────
    flatpakDep("com.guardsquare:proguard-gradle:7.7.0")
    flatpakDep("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    flatpakDep("org.jetbrains.kotlin:kotlin-stdlib-common:2.1.0")

    detektPlugins(libs.detekt.formatting)

    // ── Older transitive versions needed for plugin resolution metadata ─────
    flatpakDep("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    flatpakDep("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20")
    flatpakDep("com.google.guava:guava:32.1.3-jre")
    flatpakDep("com.google.guava:guava-parent:32.1.3-jre")
    flatpakDep("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    flatpakDep("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.9.0")
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

tasks.flatpakGradleGenerator {
    outputFile = file("../../flatpak-sources-convention.json")
    downloadDirectory.set("./offline-repository")
    includeConfigurations.set(setOf("compileClasspath", "flatpakOfflineDeps"))
}

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

        register("publishing") {
            id = "meshtastic.publishing"
            implementationClass = "PublishingConventionPlugin"
        }

        register("aboutLibraries") {
            id = "meshtastic.aboutlibraries"
            implementationClass = "AboutLibrariesConventionPlugin"
        }
    }
}
