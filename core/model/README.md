# `:core:model`

## Module dependency graph

<!--region graph-->
```mermaid
graph TB
  :core:model[model]:::null

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-application-compose fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef android-library fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-library-compose fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-test fill:#A0C4FF,stroke:#000,stroke-width:2px,color:#000;
classDef jvm-library fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library fill:#FFC1CC,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;

```
<!--endregion-->

## Meshtastic Core Models

This module contains the Parcelable data classes used by the Meshtastic Android app and its API. These models are designed to be shared between the service and client applications via AIDL.

### Key Classes

*   **`DataPacket`**: Represents a mesh packet (text, telemetry, etc.).
*   **`MeshUser`**: Represents a user/node on the mesh.
*   **`NodeInfo`**: Contains detailed information about a node (position, SNR, battery, etc.).
*   **`Position`**: GPS location data.

### Usage

This module is typically used as a dependency of `core:api` but can be used independently if you need to work with Meshtastic data structures.

```kotlin
implementation("com.github.meshtastic.Meshtastic-Android:meshtastic-android-model:v2.7.13")
```
