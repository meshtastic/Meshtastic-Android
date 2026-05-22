# `:core:prefs`

## Overview
The `:core:prefs` module provides a type-safe preferences layer backed by DataStore (multiplatform). On Android, legacy `SharedPreferences` are automatically migrated to DataStore on first access via `SharedPreferencesMigration`.

## Key Components

### 1. DataStore Providers (`CorePrefsAndroidModule`)
Provides named `DataStore<Preferences>` singletons for each preference domain (analytics, app, map, mesh, radio, UI, etc.). Each DataStore uses an injected `CoroutineDispatchers.io` scope and includes a `SharedPreferencesMigration` for seamless migration from the legacy preference files.

### 2. Specialized Prefs
- **`RadioPrefs`**: Manages radio-specific settings (e.g., the last connected device address).
- **`UiPrefs`**: Manages UI preferences (e.g., theme selection, unit systems).
- **`MapPrefs`**: Manages mapping preferences (e.g., preferred map provider).

