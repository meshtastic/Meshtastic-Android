# `:core:service`

## Overview

**Targets:** Android · JVM (Desktop) · iOS

The `:core:service` module contains the abstractions and client-side logic for interacting with the main Meshtastic Android Service.

## Key Components

### 1. `ServiceClient`
The main entry point for other parts of the app (or third-party apps) to bind to and interact with the mesh service via AIDL.

### 2. `ServiceRepository`
A high-level repository that wraps the service connection and exposes reactive `Flow`s for connection status and data arrival.

### 3. `ConnectionState`
An enum representing the current state of the radio connection (`Connected`, `Disconnected`, `DeviceSleep`, etc.).

### 4. `ServiceAction`
Defines Intent actions for starting, stopping, and interacting with the background service.


## Dependency Graph

<!--region graph-->
<!--endregion-->
