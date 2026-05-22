# Firebase Remote Config Android Setup Guide

Important references:

- Refer to the `firebase-basics` skills, particularly those for project and app setup, before proceeding.

## Project and App Setup

Before you begin, ensure you have the following. If a `google-services.json` file is present, then use that Firebase project and app. Otherwise you may need to create them.

- **Firebase CLI**: Installed and logged in (see `firebase-basics`).
- **Firebase Project**: Created via `npx -y firebase-tools@latest projects:create` (see `firebase-basics`).
- **Firebase App**: Created via `npx -y firebase-tools@latest apps:create <IOS|ANDROID|WEB> <package-name-or-bundle-id>`

The `google-services.json` file must be present in the Android app's module directory. If missing, get the config using the Firebase CLI: `npx -y firebase-tools@latest apps:sdkconfig ANDROID <App-ID>`.

## Add Dependencies to Gradle Build

These changes are made to your Android project's Gradle files. Google Analytics is highly recommended as it enables conditional targeting based on user properties and audiences.

### Project-level `build.gradle.kts` (`<project>/build.gradle.kts`)

Ensure the Google Services plugin is in the `plugins` block:

```kotlin
plugins {
    // ... other plugins
    id("com.google.gms.google-services") version "4.4.0" apply false
}
```

### App-level `build.gradle.kts` (`<project>/<app-module>/build.gradle.kts`)

1.  Add the Google Services plugin to the `plugins` block:

    ```kotlin
    plugins {
        // ... other plugins
        id("com.google.gms.google-services")
    }
    ```

2.  Add the Firebase Remote Config and Analytics dependencies. Using the Firebase Bill of Materials (BoM) is the best practice for version management.

    ```kotlin
    dependencies {
        // ... other dependencies

        // Import the Firebase BoM
        implementation(platform("com.google.firebase:firebase-bom:32.7.0"))

        // Add the dependencies for Remote Config and Analytics
        implementation("com.google.firebase:firebase-config-ktx")
        implementation("com.google.firebase:firebase-analytics-ktx")
    }
    ```


## Follow up Steps

The following steps cover the essential patterns for using Remote Config effectively.

### Set In-App Defaults
Define default values so your app has functional logic before it ever fetches a template from the server. Create an XML file (e.g., `res/xml/remote_config_defaults.xml`):
    ```xml
    <!-- Example Remote Config Defaults File -->
    <?xml version="1.0" encoding="utf-8"?>
    <defaultsMap>
        <entry>
            <key>welcome_message</key>
            <value>Welcome to the app!</value>
        </entry>
        <entry>
            <key>is_feature_enabled</key>
            <value>false</value>
        </entry>
    </defaultsMap>
    ```
Then, initialize the SDK in your Activity or Application class:

    ```kotlin
    val remoteConfig = Firebase.remoteConfig
    remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
    ```


### Fetch and Activate Values
To apply values from the cloud, you must fetch them and then activate them.
    ```kotlin
    remoteConfig.fetchAndActivate()
    .addOnCompleteListener(this) { task ->
        if (task.isSuccessful) {
            val updated = task.result
            println("Config params updated: $updated")
        } else {
            println("Fetch failed")
        }
        // Access a value
        val message = remoteConfig.getString("welcome_message")
    }
    ```
