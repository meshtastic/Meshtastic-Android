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

pluginManagement {
    includeBuild("build-logic/settings-plugin")
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("./offline-repository") }
    }
}

plugins {
    // Develocity + CCUD + build cache; shared with build-logic, versions from the catalog.
    id("meshtastic.develocity")
    id("org.gradle.toolchains.foojay-resolver") version "1.0.0"
    // 0.1.7 fixed the Isolated Projects incompatibility (shares state via a BuildService instead of
    // gradle.extensions) that previously required gating this behind an opt-in property.
    // 0.2.0 resolves platformDependencies transitively — see the collapsed list in build.gradle.kts.
    id("org.meshtastic.flatpak.sources.settings") version "0.2.1"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        // Only enable mavenLocal for local JitPack testing; never in CI.
        if (providers.gradleProperty("useMavenLocal").isPresent) mavenLocal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            mavenContent { snapshotsOnly() }
        }
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroupByRegex("com\\.github\\..*")
            }
        }
        maven { url = uri("./offline-repository") }

        // NOTE: a webApp-milestone pass tried adding exclusiveContent{ivy(...)} blocks here for
        // nodejs.org/dist, yarnpkg and WebAssembly/binaryen releases, to satisfy Kotlin/Wasm's NodeJs/Yarn/
        // Binaryen setup plugins under FAIL_ON_PROJECT_REPOS above. It did NOT work: those plugins'
        // AbstractSetupTask registers its own project-level Ivy repository at project-evaluation/task-
        // configuration time (a different code path than what a settings-level declaration intercepts), so
        // :webApp:wasmJsBrowserDistribution still failed with "was added by unknown code" even with this
        // block in place. Reverted rather than leaving dead config in place. See
        // .agent_plans/web-target-workpad.md's webApp milestone entry for the full diagnosis and the options
        // a future pass needs to choose between (relax FAIL_ON_PROJECT_REPOS, or find a way to make the
        // plugin consume a pre-registered repository).
    }
}

rootProject.name = "MeshtasticAndroid"

// https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:type-safe-project-accessors
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")


@Suppress("UnstableApiUsage")
toolchainManagement {
    jvm {
        javaRepositories {
            repository("foojay") {
                resolverClass.set(org.gradle.toolchains.foojay.FoojayToolchainResolver::class.java)
            }
        }
    }
}

include(
    ":core:ble",
    ":core:common",
    ":core:data",
    ":core:database",
    ":core:datastore",
    ":core:di",
    ":core:domain",
    ":core:konsist",
    ":core:model",
    ":core:navigation",
    ":core:network",
    ":core:nfc",
    ":core:prefs",
    ":core:repository",
    ":core:service",
    ":core:resources",
    ":core:takserver",
    ":core:testing",
    ":core:ui",
    ":feature:intro",
    ":feature:messaging",
    ":feature:connections",
    ":feature:map",
    ":feature:map-maplibre",
    ":feature:node",
    ":feature:settings",
    ":feature:discovery",
    ":feature:docs",
    ":feature:firmware",
    ":feature:wifi-provision",
    ":desktopApp",
    ":androidApp",
    // ":webApp", — NOT included yet. webApp/ has a complete, compiling module (verified via
    // :webApp:compileKotlinWasmJs run directly against its own build.gradle.kts with an ad-hoc
    // includeBuild-style check), but merely adding it to this include list breaks the ROOT-LEVEL baseline
    // gate (`./gradlew spotlessCheck detekt test allTests`, unscoped) for every other module in the repo,
    // confirmed by an actual run: it fails at configuration time, before any task executes, with
    // "IllegalStateException: :core:common is not configured for JS usage" thrown from
    // KotlinRootNpmResolver. Isolated experimentally (2026-08-31): the trigger is specifically
    // `binaries.executable()`, not `browser()` — with `:webApp` included but `binaries.executable()` removed,
    // `./gradlew :core:common:help --dry-run` and `./gradlew projects` both configure cleanly. So the conflict
    // is inherent to declaring an executable Kotlin/Wasm binary anywhere in a multi-project build that also
    // contains other wasmJs-target subprojects — every other wasmJs-enabled module deliberately stays at bare
    // wasmJs() (see core:prefs/build.gradle.kts's comment), and that registers a root-level kotlinWasmNpmInstall
    // task that walks the WHOLE build's wasmJs dependency graph regardless of which task you actually invoke.
    // The likely escape hatch is a Gradle composite build (webApp as its own build via includeBuild(), consuming
    // the libraries through dependency substitution rather than as a subproject) — NOT attempted here: it would
    // require sharing build-logic's convention plugins and the version catalog across build boundaries and
    // substituting ~15 transitive project() dependencies, which is a real restructuring, not a quick fix, and
    // risks being wrong if done blind at the tail of this effort. Re-add this line only after that is resolved
    // (see .agent_plans/web-target-workpad.md's webApp milestone entry for the full diagnosis and options).
    ":core:barcode",
    ":feature:widget",
    ":screenshot-tests",
    ":docs-screenshots",
    ":baselineprofile",
)
