# Meshtastic Android API

This module contains the stable AIDL interface and dependencies required to integrate with the Meshtastic Android app.

## Integration

To communicate with the Meshtastic Android service from your own application, we recommend using **JitPack**.

Add the JitPack repository to your root `build.gradle.kts` (or `settings.gradle.kts`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependencies to your module's `build.gradle.kts`:

```kotlin
dependencies {
    // Replace 'v2.7.12' with the specific version you need
    val meshtasticVersion = "v2.7.12" 

    // The core AIDL interface
    implementation("com.github.meshtastic.Meshtastic-Android:core-api:$meshtasticVersion")
    
    // Data models (DataPacket, MeshUser, NodeInfo, etc.)
    implementation("com.github.meshtastic.Meshtastic-Android:core-model:$meshtasticVersion")
    
    // Protobuf definitions (Portnums, Telemetry, etc.)
    implementation("com.github.meshtastic.Meshtastic-Android:core-proto:$meshtasticVersion")
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
