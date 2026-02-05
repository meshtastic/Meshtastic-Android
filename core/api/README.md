# Meshtastic Android API

This module contains the stable AIDL interface and dependencies required to integrate with the Meshtastic Android app.

## Integration

[![](https://jitpack.io/v/meshtastic/Meshtastic-Android.svg)](https://jitpack.io/#meshtastic/Meshtastic-Android)

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
    // Replace 'v2.7.13' with the specific version you need
    val meshtasticVersion = "v2.7.13" 

    // The core AIDL interface
    implementation("com.github.meshtastic.Meshtastic-Android:core-api:$meshtasticVersion")
    
    // Data models (DataPacket, MeshUser, NodeInfo, etc.)
    implementation("com.github.meshtastic.Meshtastic-Android:core-model:$meshtasticVersion")
    
    // Protobuf definitions (PortNum, Telemetry, etc.)
    implementation("com.github.meshtastic.Meshtastic-Android:core-proto:$meshtasticVersion")
}
```

## Usage

### 1. Bind to the Service

Use the `IMeshService` interface to bind to the Meshtastic service. It is recommended to query the package manager to find the correct service component, as the package name may vary between build flavors (e.g., Play Store vs. F-Droid).

```kotlin
val intent = Intent("com.geeksville.mesh.Service")
val resolveInfo = packageManager.queryIntentServices(intent, 0)

if (resolveInfo.isNotEmpty()) {
    val serviceInfo = resolveInfo[0].serviceInfo
    intent.setClassName(serviceInfo.packageName, serviceInfo.name)
    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
}
```

### 2. Interact with the API

Once bound, cast the `IBinder` to `IMeshService`:

```kotlin
override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
    val meshService = IMeshService.Stub.asInterface(service)
    
    // Example: Send a broadcast text message
    val packet = DataPacket(
        to = DataPacket.ID_BROADCAST,
        bytes = "Hello Meshtastic!".encodeToByteArray().toByteString(),
        dataType = PortNum.TEXT_MESSAGE_APP.value,
        id = meshService.packetId,
        wantAck = true
    )
    meshService.send(packet)
}
```

### 3. Register a BroadcastReceiver

To receive packets and status updates, register a `BroadcastReceiver`. 

**Important:** On Android 13+ (API 33), you **must** use `RECEIVER_EXPORTED` since you are receiving broadcasts from a different application.

```kotlin
val intentFilter = IntentFilter().apply {
    addAction("com.geeksville.mesh.RECEIVED.TEXT_MESSAGE_APP")
    addAction("com.geeksville.mesh.NODE_CHANGE")
    addAction("com.geeksville.mesh.CONNECTION_CHANGED")
}

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    registerReceiver(meshtasticReceiver, intentFilter, Context.RECEIVER_EXPORTED)
} else {
    registerReceiver(meshtasticReceiver, intentFilter)
}
```

## Modules

*   **`core:api`**: Contains `IMeshService.aidl`.
*   **`core:model`**: Contains Parcelable data classes like `DataPacket`, `MeshUser`, `NodeInfo`.
*   **`core:proto`**: Contains the generated Protobuf code (Wire).
