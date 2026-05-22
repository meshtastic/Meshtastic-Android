# `:core:common`

## Overview

**Targets:** Android · JVM (Desktop) · iOS

The `:core:common` module contains low-level utility functions, extensions, and common data structures that do not depend on any other Meshtastic-specific modules. It is designed to be highly reusable across the project.

## Key Components

### 1. `util` package
Contains general-purpose extensions and helpers:
- **Coroutines**: Helpers for structured concurrency and Flow transformations.
- **Time**: Utilities for handling timestamps and durations.
- **Exceptions**: Standardized exception types for common error scenarios.

### 2. `MetricFormatter.kt`
Centralized utility for display strings — temperature, voltage, current, percent, humidity, pressure, SNR, RSSI. Ensures consistent unit spacing and formatting across all UI surfaces.

### 3. `BuildConfigProvider.kt`
An interface for accessing build-time configuration in a multiplatform-friendly way.

