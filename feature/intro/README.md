# `:feature:intro`

## Overview

**Targets:** Android · JVM (Desktop) · iOS

The `:feature:intro` module provides the onboarding experience for new users. It handles the initial welcome flow and requests mandatory permissions (Location, Bluetooth, Notifications).

## Key Components

### 1. `AppIntroductionScreen`
Orchestrates the multi-step onboarding process.

### 2. Permission Screens
Dedicated screens for explaining and requesting specific permissions:
- `LocationScreen`: Necessary for mapping and BLE scanning (on older Android versions).
- `BluetoothScreen`: Necessary for connecting to radios.
- `NotificationsScreen`: Necessary for foreground service and message alerts.

