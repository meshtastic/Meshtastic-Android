# `:core:prefs`

## Overview
The `:core:prefs` module provides a type-safe wrapper around `SharedPreferences` for managing application and radio configuration preferences.

## Key Components

### 1. `PrefDelegate.kt`
Uses Kotlin property delegates to simplify reading and writing preferences.

### 2. Specialized Prefs
- **`RadioPrefs`**: Manages radio-specific settings (e.g., the last connected device address).
- **`UiPrefs`**: Manages UI preferences (e.g., theme selection, unit systems).
- **`MapPrefs`**: Manages mapping preferences (e.g., preferred map provider).

## Module dependency graph

<!--region graph-->
```mermaid
graph TB
  :core:prefs[prefs]:::android-library
  :core:prefs -.-> :core:repository

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
