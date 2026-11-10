# `:core:prefs`

## Overview
The `:core:prefs` module provides a type-safe preferences layer backed by DataStore (multiplatform). On Android, legacy `SharedPreferences` are automatically migrated to DataStore on first access via `SharedPreferencesMigration`.

## Key Components

### 1. DataStore Providers (`CorePrefsAndroidModule`)
Provides one `DataStore<Preferences>` singleton per preference domain (analytics, app, map, mesh, radio, UI, etc.), each under its own type (`AnalyticsDataStore`, `AppDataStore`, … in `PrefsDataStores.kt`) rather than a Koin string qualifier. Each DataStore uses an injected `CoroutineDispatchers.io` scope and includes a `SharedPreferencesMigration` from the legacy preference file. The Desktop host provides the same types from `desktopApp`.

### 2. Specialized Prefs
Each `*PrefsImpl` here implements an interface from `:core:repository`'s `AppPreferences.kt`:
- **`RadioPrefsImpl`**: The last connected device address and name.
- **`UiPrefsImpl`**: UI preferences (for example theme, units override, locale, node sort and filters).
- **`MapPrefsImpl`**: Mapping preferences (for example map style and layer visibility).
- Others for analytics, notifications, mesh, the Local TAK Server (`TakPrefsImpl`), discovery, custom emoji, and AppFunctions.


## Dependency Graph

<!--region graph-->
```mermaid
graph TB
  :core:prefs[prefs]:::kmp-library
  :core:prefs -.-> :core:repository
  :core:prefs -.-> :core:common
  :core:prefs -.-> :core:di

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-application-compose fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef compose-desktop-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef android-library fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-library-compose fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-test fill:#A0C4FF,stroke:#000,stroke-width:2px,color:#000;
classDef jvm-library fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library-compose fill:#FFC1CC,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library fill:#FFC1CC,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;

```
<!--endregion-->
