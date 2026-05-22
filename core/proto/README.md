# `:core:proto`

## Overview

**Targets:** Android · JVM (Desktop) · iOS

This module contains the generated Kotlin and Java code from the Meshtastic Protobuf definitions. It uses the [Wire](https://github.com/square/wire) library for efficient and clean model generation.

## Key Components

- **`PortNum`**: Defines the identification for different types of data payloads.
- **`MeshPacket`**: The core protocol message definition.
- **Protobuf Modules**: Definitions for telemetry, position, administration, and more.

## Usage
This module is a low-level dependency for any module that needs to encode or decode Meshtastic protocol data.

```kotlin
implementation(projects.core.proto)
```


## Dependency Graph

<!--region graph-->
<!--endregion-->
