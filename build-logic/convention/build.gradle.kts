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

val flatpakOfflineDeps by
    configurations.creating {
        isCanBeResolved = true
        isCanBeConsumed = false
    }

val flatpakOfflineEmbeddedDeps by
    configurations.creating {
        isCanBeResolved = true
        isCanBeConsumed = false
    }

val flatpakOfflineOlderDeps by
    configurations.creating {
        isCanBeResolved = true
        isCanBeConsumed = false
    }

dependencies {
    // This allows the use of the 'libs' type-safe accessor in the Kotlin source of the plugins
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    // Self-updating embedded Gradle Kotlin version
    val gradleKotlinVersion = KotlinVersion.CURRENT.toString()

    /** Registers a dependency to be captured for the Flatpak offline repository. */
    fun flatpakDep(dependency: Any) {
        flatpakOfflineDeps(dependency)
    }

    /** Registers a Gradle embedded dependency to be captured for Flatpak offline. */
    fun flatpakEmbeddedDep(dependency: Any) {
        flatpakOfflineEmbeddedDeps(dependency)
    }

    /** Registers an older dependency to be captured for Flatpak offline. */
    fun flatpakOlderDep(dependency: Any) {
        flatpakOfflineOlderDeps(dependency)
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

    // ── Dynamic plugin marker artifacts from Version Catalog ────────────────
    // This loop dynamically resolves and registers all plugin marker artifacts declared in the catalog.
    // Since Develocity, Custom User Data, and Foojay Resolver are defined in libs.versions.toml,
    // they are automatically resolved and registered here!
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
    flatpakDep("${libs.kotlin.build.tools.compat.get().module}:$gradleKotlinVersion")
    flatpakDep("${libs.kotlin.build.tools.impl.get().module}:$gradleKotlinVersion")
    flatpakDep("${libs.kotlin.scripting.compiler.embeddable.get().module}:$gradleKotlinVersion")
    flatpakDep("${libs.kotlin.sam.with.receiver.compiler.plugin.embeddable.get().module}:$gradleKotlinVersion")
    flatpakDep("${libs.kotlin.assignment.compiler.plugin.embeddable.get().module}:$gradleKotlinVersion")
    flatpakDep(libs.kotlin.build.tools.compat)
    flatpakDep(libs.kotlin.build.tools.impl)
    flatpakDep(libs.kotlin.scripting.compiler.embeddable)
    // Gradle-embedded compiler versions (2.3.20) required to compile precompiled scripts in offline mode
    // We register these in a separate configuration so that Gradle does not version-align/upgrade them
    // to our project's newer Kotlin version (2.3.21) during flatpak offline source generation.
    flatpakEmbeddedDep(libs.gradle.kotlin.build.tools.compat)
    flatpakEmbeddedDep(libs.gradle.kotlin.build.tools.impl)
    flatpakEmbeddedDep(libs.gradle.kotlin.scripting.compiler.embeddable)
    flatpakEmbeddedDep(libs.gradle.kotlin.sam.with.receiver.compiler.plugin.embeddable)
    flatpakEmbeddedDep(libs.gradle.kotlin.assignment.compiler.plugin.embeddable)

    // ── Compiler plugins resolved at task execution time ────────────────────
    flatpakDep(libs.koin.compiler.plugin)
    flatpakDep(libs.kotlin.compose.compiler.plugin.embeddable)
    flatpakDep(libs.kotlin.serialization.compiler.plugin.embeddable)

    // ── Transitive deps not on standard classpaths ──────────────────────────
    flatpakDep(libs.compose.material.ripple)
    flatpakDep(libs.savedstate.compose)
    flatpakDep(libs.androidx.paging.common)

    // ── Compose Desktop packaging (proguardReleaseJars task) ────────────────
    flatpakDep(libs.proguard.gradle)
    flatpakDep(libs.kotlin.stdlib)
    flatpakDep(libs.kotlin.stdlib.common)

    detektPlugins(libs.detekt.formatting)

    // ── Older transitive versions needed for plugin resolution metadata ─────
    // We register these in a separate configuration so they don't get upgraded to newer versions
    // by Gradle's dependency conflict resolution strategy.
    flatpakOlderDep(libs.old.kotlin.gradle.plugin)
    flatpakOlderDep(libs.old.kotlin.stdlib.jdk8)
    flatpakOlderDep(libs.old.guava)
    flatpakOlderDep(libs.old.guava.parent)
    flatpakOlderDep(libs.old.kotlinx.serialization.core)
    flatpakOlderDep(libs.old.kotlinx.serialization.core.jvm)
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
    includeConfigurations.set(
        setOf("compileClasspath", "flatpakOfflineDeps", "flatpakOfflineEmbeddedDeps", "flatpakOfflineOlderDeps"),
    )
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
