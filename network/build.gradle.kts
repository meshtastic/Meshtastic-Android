import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.devtools.ksp)
    alias(libs.plugins.detekt)
    id("kotlinx-serialization")
}

android {
    buildFeatures {
        buildConfig = true
    }
    compileSdk = 35
    defaultConfig {
        minSdk = 21
    }

    namespace = "com.geeksville.mesh.network"
    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_17)
        targetCompatibility(JavaVersion.VERSION_17)
    }

    flavorDimensions += "default"
    productFlavors {
        create("fdroid") {
            dimension = "default"
        }
        create("google") {
            dimension = "default"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.bundles.hilt)
    implementation(libs.bundles.retrofit)
    implementation(libs.bundles.coil)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)
    detektPlugins(libs.detekt.formatting)
}

detekt {
    config.setFrom("../config/detekt/detekt.yml")
    baseline = file("../config/detekt/detekt-baseline.xml")
}
