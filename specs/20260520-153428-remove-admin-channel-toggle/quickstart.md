# Quickstart: Remove Admin Channel Enabled Toggle

## Overview

Remove the `admin_channel_enabled` toggle from the Security Config screen by deleting 8 lines of Compose UI code and 1 unused import.

## Prerequisites

- JDK 21 installed
- `ANDROID_HOME` set
- Proto submodule initialized (`git submodule update --init`)

## Implementation Steps

### Step 1: Remove unused import (Line 49)

In `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/radio/component/SecurityConfigScreen.kt`:

```kotlin
// DELETE this line:
import org.meshtastic.core.resources.legacy_admin_channel
```

### Step 2: Remove toggle block (Lines 207–214)

In the same file, inside the `TitledCard(title = stringResource(Res.string.administration))` block, delete:

```kotlin
// DELETE these 8 lines:
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.legacy_admin_channel),
                    checked = formState.value.admin_channel_enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(admin_channel_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
```

### Step 3: Verify

```bash
# Compile all KMP targets for the settings module
./gradlew :feature:settings:allTests :feature:settings:compileKotlinJvm

# Full lint check
./gradlew spotlessApply spotlessCheck detekt
```

## Expected Result

- Security Config screen renders without the admin channel toggle
- "Administration" TitledCard shows only the "Managed Mode" switch
- All existing tests pass
- No lint violations
