# Meshtastic Android API

This module contains the stable AIDL interface and dependencies required to integrate with the Meshtastic Android app.

## Integration

To communicate with the Meshtastic Android service from your own application, add the following dependencies to your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    // If using GitHub Packages (until published to Maven Central)
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/meshtastic/Meshtastic-Android")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    // The core AIDL interface
    implementation("org.meshtastic:core-api:2.7.12") // Match the Meshtastic app version
    
    // Data models (DataPacket, MeshUser, NodeInfo, etc.)
    implementation("org.meshtastic:core-model:2.7.12")
    
    // Protobuf definitions (Portnums, Telemetry, etc.)
    implementation("org.meshtastic:core-proto:2.7.12")
}
```

## Usage

1.  **Bind to the Service:**
    Use the `IMeshService` interface to bind to the Meshtastic service.

    ```kotlin
    val intent = Intent("com.geeksville.mesh.Service")
    intent.setClassName("com.geeksville.mesh", "com.geeksville.mesh.service.MeshService")
    bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    ```

2.  **Interact with the API:**
    Once bound, cast the `IBinder` to `IMeshService`:

    ```kotlin
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val meshService = IMeshService.Stub.asInterface(service)
        
        // Example: Send a text message
        val packet = DataPacket(
            to = DataPacket.ID_BROADCAST,
            bytes = "Hello Meshtastic!".toByteArray(),
            dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
            // ... other fields
        )
        meshService.send(packet)
    }
    ```

## Modules

*   **`core:api`**: Contains `IMeshService.aidl`.
*   **`core:model`**: Contains Parcelable data classes like `DataPacket`, `MeshUser`, `NodeInfo`.
*   **`core:proto`**: Contains the generated Protobuf code from `meshtastic/protobufs`.
