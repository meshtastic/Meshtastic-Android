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

plugins {
    alias(libs.plugins.meshtastic.kmp.library)
    alias(libs.plugins.meshtastic.koin)
}

kotlin {
    android { withHostTest {} }

    // wasmJs { browser() } required repo-wide by KGP's root npm resolver — see core:prefs/build.gradle.kts's comment.
    // No custom
    // hierarchy GROUP is needed for MAIN: Location's android/jvm/ios actuals already each live in their own
    // independent source set (no shared nonWeb code to hide from wasmJs), so a 4th, wasmJsMain/Location.kt, is
    // all this module needs.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    // The plain default template call IS still required, though, once any source set below declares a manual
    // `dependsOn` edge (nonWebTest, see sourceSets block): KGP only auto-applies the default hierarchy template
    // (which is what wires each leaf iOS compilation's *Main source set to the shared `iosMain` that
    // src/iosMain/kotlin/.../Location.kt actually lives in) when NO source set anywhere in the project has a
    // manual `dependsOn` edge. Adding `nonWebTest`'s manual edges without this call silently orphaned `iosMain`
    // from `iosArm64Main`/`iosSimulatorArm64Main`, breaking Location's actual resolution for Native
    // (`compileKotlinIosSimulatorArm64`: "Expected Location has no actual declaration in module <commonMain> for
    // Native") — caught by gradle-runner verification, fixed by restoring the plain (unfiltered) call here.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate.default)

    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(libs.meshtastic.protobufs)
            implementation(projects.core.common)
            implementation(projects.core.database)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kermit)
            implementation(libs.androidx.paging.common)
        }
        // kotlin("test")/kotlinx-coroutines-test only — previously came transitively through core:testing's own
        // api() exposure, which commonTest can no longer depend on directly (see nonWebTest below). All 9
        // remaining commonTest files need kotlin("test"); 3 of them also need coroutines-test.
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        // android/jvm/ios only: core:testing has no wasmJs target. Only AppPreferencesTest/SendMessageUseCaseTest
        // (of 11 commonTest files, confirmed via grep for the import) actually reference it — moved to a new
        // nonWebTest source set. No applyHierarchyTemplate call exists in this module (unlike core:ble/
        // core:database), so nonWebTest is created manually and wired to the leaf test source sets directly,
        // matching core:domain/core:service/feature:node's identical precedent for a module with no MAIN split.
        val nonWebTest by creating {
            dependsOn(commonTest.get())
            dependencies { implementation(projects.core.testing) }
        }
        getByName("jvmTest") { dependsOn(nonWebTest) }
        getByName("androidHostTest") { dependsOn(nonWebTest) }
        // matching{} degrades to a harmless no-op on a host where Apple targets aren't registered (e.g.
        // linux-aarch64 CI), instead of throwing the way a hardcoded getByName("iosArm64Test") would.
        matching { it.name == "iosArm64Test" || it.name == "iosSimulatorArm64Test" }
            .configureEach { dependsOn(nonWebTest) }
    }
}
