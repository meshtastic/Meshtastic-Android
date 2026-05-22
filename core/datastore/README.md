# `:core:datastore`

## Overview

**Targets:** Android · JVM (Desktop) · iOS

The `:core:datastore` module manages structured, asynchronous data storage using **Jetpack DataStore**. It is primarily used for storing complex configuration objects like radio channel sets and local device configurations.

## Key Components

### 1. Data Sources
- **`ChannelSetDataSource`**: Manages the storage of radio channel configurations.
- **`RecentAddressesDataSource`**: Stores a list of recently connected radio addresses (BLE/USB/TCP).
- **`UiPreferencesDataSource`**: Modern replacement for `SharedPreferences` for UI-related settings.

### 2. Serializers
Uses **Kotlin Serialization** to convert between Protobuf/JSON and the underlying DataStore storage.


## Dependency Graph

<!--region graph-->
<!--endregion-->
