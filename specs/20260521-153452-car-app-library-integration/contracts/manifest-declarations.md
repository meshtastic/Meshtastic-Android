# Manifest Declarations Contract

**Feature**: Car App Library Integration
**Date**: 2026-05-21

## feature/car/src/main/AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Car App Library service declaration -->
    <application>
        <service
            android:name="org.meshtastic.feature.car.service.MeshtasticCarAppService"
            android:exported="true">
            <intent-filter>
                <action android:name="androidx.car.app.CarAppService" />
                <category android:name="androidx.car.app.category.MESSAGING" />
                <!-- POI or NAVIGATION category deferred pending map strategy decision -->
            </intent-filter>
        </service>

        <!-- Minimum Car API Level for 1.9.0-alpha01 components -->
        <meta-data
            android:name="androidx.car.app.minCarApiLevel"
            android:value="8" />
    </application>
</manifest>
```

## AAOS Support: automotive_app_desc.xml

Located at `feature/car/src/main/res/xml/automotive_app_desc.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<automotiveApp>
    <uses name="template" />
</automotiveApp>
```

## androidApp Manifest Additions (google flavor only)

In `androidApp/src/google/AndroidManifest.xml` (or merged automatically via manifest merger):

```xml
<!-- No additional declarations needed — the feature/car manifest merges automatically
     when the module is included as a dependency in the google flavor -->
```

## Gradle Dependency Declaration

In `androidApp/build.gradle.kts`:

```kotlin
dependencies {
    // Car module (google flavor only - CAL requires Play Services)
    "googleImplementation"(projects.feature.car)
}
```

In `settings.gradle.kts` (new include):

```kotlin
include(":feature:car")
```

## Version Catalog Additions (gradle/libs.versions.toml)

```toml
[versions]
car-app = "1.9.0-alpha01"

[libraries]
androidx-car-app = { module = "androidx.car.app:app", version.ref = "car-app" }
androidx-car-app-projected = { module = "androidx.car.app:app-projected", version.ref = "car-app" }
androidx-car-app-automotive = { module = "androidx.car.app:app-automotive", version.ref = "car-app" }
androidx-car-app-testing = { module = "androidx.car.app:app-testing", version.ref = "car-app" }
```

## feature/car/build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.meshtastic.android.library)
    alias(libs.plugins.meshtastic.android.library.flavors)
    alias(libs.plugins.meshtastic.koin)
}

android {
    namespace = "org.meshtastic.feature.car"

    defaultConfig {
        minSdk = 23  // Android Auto projection minimum
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(projects.core.repository)
    implementation(projects.core.ble)

    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)

    implementation(libs.koin.android)
    implementation(libs.koin.annotations)

    implementation(libs.firebase.crashlytics)

    testImplementation(libs.androidx.car.app.testing)
    testImplementation(libs.koin.test)
    testImplementation(kotlin("test"))
}
```

## Permissions

No additional permissions required. The car module:
- Does NOT request `BLUETOOTH` permissions (handled by `core/ble` at the app level)
- Does NOT request location permissions (handled by existing app permissions)
- Does NOT request microphone permissions (CAL voice input is delegated to the system)

## ProGuard / R8 Rules

```proguard
# Car App Library service must not be obfuscated (resolved by exported service)
-keep class org.meshtastic.feature.car.service.MeshtasticCarAppService { *; }
```
