# `:feature:settings`

## Overview
The `:feature:settings` module manages all application and radio-side configurations. This includes user preferences, channel configuration, and advanced radio settings.

## Key Components

### 1. `SettingsScreen`
The main entry point for application-wide settings.

### 2. `RadioConfigViewModel`
Handles the complex logic of reading and writing configuration to the Meshtastic device over the radio link (BLE, USB, or TCP).

### 3. `AboutScreen`
Displays version information, licenses, and project links.

## Features
- **Channel Configuration**: Manage encryption keys, channel names, and radio frequency settings.
- **Node Database Management**: Options to clear or prune the local and remote node databases.
- **App Preferences**: Theme selection, unit system (metric/imperial), and notification settings.


## Dependency Graph

<!--region graph-->
<!--endregion-->
