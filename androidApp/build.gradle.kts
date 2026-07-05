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

import com.android.build.api.dsl.ApplicationExtension
import org.meshtastic.buildlogic.configProperties
import org.meshtastic.buildlogic.resolveVersionInfo
import java.util.Properties

val versionInfo = resolveVersionInfo()

plugins {
    alias(libs.plugins.meshtastic.android.application)
    alias(libs.plugins.meshtastic.android.application.flavors)
    alias(libs.plugins.meshtastic.android.application.compose)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
    id("meshtastic.koin")
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.secrets)
    alias(libs.plugins.androidx.baselineprofile)
    id("meshtastic.aboutlibraries")
    id("dev.mokkery")
    alias(libs.plugins.devtools.ksp)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

// The templated Android Auto experience (CarAppService + HomeScreen) is a Google "templated
// messaging" beta feature that is publishable only to Closed/Internal tracks — Open/Production
// submissions are auto-rejected (https://developer.android.com/training/cars/communication/templated-messaging).
// Default builds therefore ship *notification-only* car messaging, which is GA and production-safe.
// Build a Closed-track templated AAB with: -PenableCarTemplates=true
// ponytail: gated by a gradle property + res override, not a full build flavor — templated is
// parked until it leaves Google's beta. Promote to a flavor dimension only if CI must ship both.
val enableCarTemplates = (findProperty("enableCarTemplates") as String?)?.toBoolean() ?: false

configure<ApplicationExtension> {
    namespace = "org.meshtastic.app"

    // When templates are enabled, this res dir overrides feature:car's notification-only
    // automotive_app_desc.xml with one that also declares <uses name="template" />.
    if (enableCarTemplates) {
        sourceSets.getByName("google").res.srcDir("src/googleCarTemplates/res")
    }

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"] as String?
            storeFile = keystoreProperties["storeFile"]?.let { file(it) }
            storePassword = keystoreProperties["storePassword"] as String?
        }
    }
    defaultConfig {
        applicationId = configProperties.getProperty("APPLICATION_ID")

        versionCode = versionInfo.versionCode
        versionName = versionInfo.versionName
        buildConfigField("String", "MIN_FW_VERSION", "\"${versionInfo.minFwVersion}\"")
        buildConfigField("String", "ABS_MIN_FW_VERSION", "\"${versionInfo.absMinFwVersion}\"")
        // We have to list all translated languages here,
        // because some of our libs have bogus languages that google play
        // doesn't like and we need to strip them (gr)
        val ci = providers.gradleProperty("ci").map { it.toBoolean() }.getOrElse(false)
        if (ci) {
            logger.lifecycle("CI build detected - limiting locale filters for faster packaging")
            androidResources.localeFilters.addAll(listOf("en"))
        } else {
            androidResources.localeFilters.addAll(
                listOf(
                    "en",
                    "ar",
                    "bg",
                    "ca",
                    "cs",
                    "de",
                    "el",
                    "es",
                    "et",
                    "fi",
                    "fr",
                    "ga",
                    "gl",
                    "hr",
                    "ht",
                    "hu",
                    "is",
                    "it",
                    "iw",
                    "ja",
                    "ko",
                    "lt",
                    "nl",
                    "no",
                    "pl",
                    "pt",
                    "pt-rBR",
                    "ro",
                    "ru",
                    "sk",
                    "sl",
                    "sq",
                    "sr",
                    "srp",
                    "sv",
                    "tr",
                    "uk",
                    "zh-rCN",
                    "zh-rTW",
                ),
            )
        }
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }

        // Activates the (google-only) CarAppService. Off by default so production builds ship
        // notification-only car messaging; flipped on by -PenableCarTemplates=true for Closed tracks.
        manifestPlaceholders["carTemplatesEnabled"] = enableCarTemplates.toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Disable ABI splits for bundle builds or when explicitly requested via Gradle property.
    // Usage: ./gradlew :androidApp:bundleGoogleRelease -Pmeshtastic.disableAbiSplits=true
    val disableSplits = providers.gradleProperty("meshtastic.disableAbiSplits").map { it.toBoolean() }.getOrElse(false)

    // Enable ABI splits to generate smaller APKs per architecture for F-Droid/IzzyOnDroid
    splits {
        abi {
            isEnable = !disableSplits
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs (for IzzyOnDroid/F-Droid)
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles (for Google Play)
        includeInBundle = false
    }

    packaging {
        jniLibs {
            // Keep debug symbols in native libraries so reproducible builds don't depend
            // on the exact NDK version used for stripping. This avoids RB failures when
            // IzzyOnDroid/F-Droid rebuilds use a different NDK than our CI.
            // See: https://github.com/meshtastic/Meshtastic-Android/issues/3231
            keepDebugSymbols.add("**/*.so")
        }
    }

    // Configure existing product flavors (defined by convention plugin)
    // with their dynamic version names.
    productFlavors {
        configureEach {
            versionName = "${defaultConfig.versionName} (${defaultConfig.versionCode}) $name"
            if (name == "google") {
                manifestPlaceholders["MAPS_API_KEY"] = "dummy"
            }
        }
    }

    buildTypes {
        release {
            if (keystoreProperties["storeFile"] != null) {
                signingConfig = signingConfigs.named("release").get()
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
            isDebuggable = false
        }
    }
    bundle { language { enableSplit = false } }

    testOptions { unitTests { isIncludeAndroidResources = true } }
}

secrets {
    defaultPropertiesFileName = "secrets.defaults.properties"
    propertiesFileName = "secrets.properties"
}

ksp { arg("appfunctions:aggregateAppFunctions", "true") }

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.flavorName?.let { flavor -> variant.applicationId.set("com.geeksville.mesh.$flavor.debug") }
    }

    onVariants(selector().withBuildType("release")) { variant ->
        if (variant.flavorName == "google") {
            val variantNameCapped = variant.name.replaceFirstChar { it.uppercase() }
            val minifyTaskName = "minify${variantNameCapped}WithR8"
            val uploadTaskName = "uploadMapping$variantNameCapped"
            // Use tasks.names to check existence without eagerly realizing tasks
            if (tasks.names.contains(uploadTaskName) && tasks.names.contains(minifyTaskName)) {
                tasks.named(minifyTaskName).configure { finalizedBy(uploadTaskName) }
            }
        }
    }
}

dependencies {
    implementation(projects.core.ble)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.di)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.navigation)
    implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)
    implementation(projects.core.network)
    implementation(projects.core.nfc)
    implementation(projects.core.prefs)
    implementation(libs.meshtastic.protobufs)
    implementation(projects.core.service)
    implementation(projects.core.resources)
    implementation(projects.core.ui)
    implementation(projects.core.barcode)
    implementation(projects.core.takserver)
    implementation(projects.feature.intro)
    implementation(projects.feature.messaging)
    implementation(projects.feature.connections)
    implementation(projects.feature.map)
    implementation(projects.feature.node)
    implementation(projects.feature.settings)
    implementation(projects.feature.discovery)
    implementation(projects.feature.docs)
    implementation(projects.feature.firmware)
    implementation(projects.feature.wifiProvision)
    implementation(projects.feature.widget)

    implementation(libs.jetbrains.compose.material3.adaptive)
    implementation(libs.jetbrains.compose.material3.adaptive.layout)
    implementation(libs.jetbrains.compose.material3.adaptive.navigation)
    implementation(libs.androidx.appcompat)
    implementation(libs.compose.multiplatform.animation)
    implementation(libs.compose.multiplatform.material3)
    implementation(libs.compose.multiplatform.ui.tooling.preview)
    implementation(libs.compose.multiplatform.ui)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.appwidget.preview)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.jetbrains.lifecycle.viewmodel.compose)
    implementation(libs.jetbrains.lifecycle.runtime.compose)
    implementation(libs.jetbrains.navigation3.ui)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.coil)
    implementation(libs.coil.network.ktor3)
    implementation(libs.coil.svg)
    implementation(libs.androidx.core.splashscreen)
    // Installs the baseline profile produced by :baselineprofile at app startup (API < 31)
    // and lets ART honor it on first launch. On API 31+ the platform installs it automatically.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.usb.serial.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.koin.annotations)
    implementation(libs.kermit)
    implementation(libs.kotlinx.datetime)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.glance.preview)

    googleImplementation(projects.feature.car)
    googleImplementation(libs.location.services)
    googleImplementation(libs.play.services.maps)
    googleImplementation(libs.maps.compose)
    googleImplementation(libs.maps.compose.utils)
    googleImplementation(libs.maps.compose.widgets)
    // maps-compose-widgets requests androidx.compose.material:material version-less (expects a BOM
    // we exclude). Name it with a version so the version is published in the app's graph metadata.
    googleImplementation(libs.androidx.compose.material)
    googleImplementation(libs.dd.sdk.android.logs)
    googleImplementation(libs.dd.sdk.android.rum)
    googleImplementation(libs.dd.sdk.android.session.replay)
    googleImplementation(libs.dd.sdk.android.timber)
    googleImplementation(libs.dd.sdk.android.trace)
    googleImplementation(libs.dd.sdk.android.trace.otel)
    googleImplementation(platform(libs.firebase.bom))
    googleImplementation(libs.firebase.analytics)
    googleImplementation(libs.firebase.crashlytics)
    googleImplementation(libs.firebase.ai)
    googleImplementation(libs.firebase.ai.ondevice)
    googleImplementation(libs.mlkit.translate)
    googleImplementation(libs.mlkit.language.id)
    googleImplementation(libs.mlkit.genai.prompt)

    googleImplementation(libs.androidx.appfunctions)
    add("kspGoogle", libs.androidx.appfunctions.compiler)

    fdroidImplementation(libs.osmdroid.android)
    fdroidImplementation(libs.osmdroid.geopackage) { exclude(group = "com.j256.ormlite") }
    fdroidImplementation(libs.osmbonuspack)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.koin.test)
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.multiplatform.ui.test)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.glance.appwidget)
    // JVM variant provides the host-platform native library for BundledSQLiteDriver under Robolectric
    testRuntimeOnly("androidx.sqlite:sqlite-bundled-jvm:2.7.0")

    // Producer of the baseline profile consumed by the release build. The androidx.baselineprofile
    // plugin merges the generated rules into src/<variant>/generated/baselineProfiles at build time.
    baselineProfile(projects.baselineprofile)
}
