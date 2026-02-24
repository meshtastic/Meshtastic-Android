# `:core:navigation`

## Overview
The `:core:navigation` module defines the type-safe navigation structure for the entire application using Kotlin Serialization and the Jetpack Navigation library.

## Key Components

### 1. `Routes.kt`
Contains all the serializable classes and objects that represent destinations in the app.

## Features
- **Type-Safety**: Leverages Kotlin Serialization to pass data between screens without fragile Bundle keys.
- **Centralized Definition**: All routes are defined in one place to prevent circular dependencies between feature modules.

## Usage
Feature modules depend on this module to define their entry points and navigate to other features.

```kotlin
import org.meshtastic.core.navigation.MessagingRoutes

navController.navigate(MessagingRoutes.Chat(nodeId = 12345))
```

## Module dependency graph

<!--region graph-->
```mermaid
graph TB
  :core:navigation[navigation]:::android-library

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
