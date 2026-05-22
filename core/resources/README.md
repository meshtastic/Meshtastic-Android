# `:core:resources`

## Overview
The `:core:resources` module is the centralized source for all UI strings and localizable resources. It uses the **Compose Multiplatform Resource** library to provide a type-safe way to access strings.

## Key Features

- **Single Source of Truth**: All UI strings must be defined in this module, not in the `app` module or feature modules.
- **Type-Safety**: Generates a `Res` object that allows accessing strings like `Res.string.your_key` with compile-time checking.

## Usage
The library provides a standard way to access strings in Jetpack Compose.

```kotlin
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.your_string_key

Text(text = stringResource(Res.string.your_string_key))
```

