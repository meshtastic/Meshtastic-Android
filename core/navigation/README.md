# `:core:navigation`

## Overview
The `:core:navigation` module defines the type-safe Navigation 3 route model for Android and Desktop using Kotlin Serialization.

## Key Components

### 1. `Routes.kt`
Contains serializable `NavKey` route classes/objects used by shared feature graphs.

### 2. `DeepLinkRouter.kt`
Parses Meshtastic deep-link URIs and synthesizes a typed backstack (for example `/nodes/1234/device-metrics`).

### 3. `NavigationConfig.kt`
Defines `MeshtasticNavSavedStateConfig` using sealed interface hierarchies so Navigation 3 backstacks can be persisted/restored safely — new routes are auto-registered at compile time.

## Features
- **Type-Safety**: Uses serializable `NavKey` routes instead of ad-hoc string routes.
- **Deep-link synthesis**: Converts incoming URIs into typed backstacks via `DeepLinkRouter`.
- **Centralized definition**: Routes and saved-state serializers are declared in one place to avoid feature-module cycles.

## Usage
Feature modules depend on this module to define their entry points and navigate via `NavBackStack<NavKey>`.

```kotlin
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.meshtastic.core.navigation.NodesRoute

fun openNodeDetail(backStack: NavBackStack<NavKey>, destNum: Int) {
    backStack.add(NodesRoute.NodeDetail(destNum))
}
```

