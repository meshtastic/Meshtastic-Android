# mesh_service_example

This module provides an example implementation of an app that uses the [AIDL](https://developer.android.com/develop/background-work/services/aidl) Mesh Service provided by Meshtastic-Android project.

## Overview

The [AIDL](../core/service/src/main/aidl/org/meshtastic/core/service/IMeshService.aidl) is defined in the main app module and is used to interact with the mesh network.

`mesh_service_example` demonstrates how to build and integrate a custom mesh service within the Meshtastic ecosystem. It is intended as a reference for developers who want to extend or customize mesh-related functionality.

## Features
- Example service structure for mesh integration
- Sample code for service registration and communication

## Usage
1. Clone the Meshtastic-Android repository.
2. Open the project in Android Studio.
3. Explore the `mesh_service_example` module source code under `mesh_service_example/src/`.
4. Use this module as a template for your own mesh service implementations.

## Development
- To build the module, use the standard Gradle build commands:
  ```sh
  ./gradlew :mesh_service_example:build
  ```
- To run tests for this module:
  ```sh
  ./gradlew :mesh_service_example:test
  ```

## License
This example module is provided under the same license as the main Meshtastic-Android project. See the root `LICENSE` file for details.

