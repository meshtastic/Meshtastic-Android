# Contract: Lockdown UI Components

**Module**: `feature/settings`  
**Source set**: `commonMain`

## LockdownDialog (non-dismissable blocking dialog)

```kotlin
/**
 * Non-dismissable AlertDialog that blocks all app interaction when the connected
 * node is in a lockdown state requiring user action (LOCKED or NEEDS_PROVISION).
 *
 * Uses `onDismissRequest = {}` + `BackHandler` to prevent dismissal.
 * Shown when state requires auth; hidden when state transitions to Unlocked or NotApplicable.
 *
 * @param state Current lockdown state from LockdownCoordinator
 * @param onSubmitPassphrase Called with (passphrase, bootsRemaining, validUntilEpoch)
 * @param onDisconnect Called when user wants to disconnect instead of authenticating
 */
@Composable
fun LockdownDialog(
    state: LockdownState,
    onSubmitPassphrase: (ByteArray, UInt, UInt) -> Unit,
    onDisconnect: () -> Unit,
)
```

### UI States Rendered

| `LockdownState` | UI Rendering |
|-----------------|-------------|
| `NeedsProvision` | "Set Passphrase" title, passphrase field + confirm field, optional TTL fields, Submit button |
| `Locked` | "Unlock Device" title, passphrase field, optional TTL fields (hidden for unlock), Submit button, lock_reason displayed |
| `Unlocking` | Same as above with Submit disabled + loading indicator |
| `UnlockFailed(backoff=0)` | Error text "Incorrect passphrase", Submit enabled for retry |
| `UnlockFailed(backoff>0)` | Error text + countdown timer, Submit disabled until backoff expires |
| `LockNowPending` | "Locking device..." with spinner |
| `LockNowAcknowledged` | "Device locked" confirmation, auto-disconnect in progress |

### Component Details

- **Passphrase field**: `OutlinedTextField` with `visualTransformation = PasswordVisualTransformation()`, trailing eye icon to toggle visibility
- **Confirm field** (provision only): Second `OutlinedTextField` with match validation
- **Boots remaining** (optional): `OutlinedTextField` with `keyboardType = KeyboardType.Number`, hint "Leave empty for default"
- **Hours until expiry** (optional): `OutlinedTextField` with number input, converted to `valid_until_epoch` (current time + hours * 3600)
- **Submit button**: `FilledTonalButton`, disabled during backoff or when passphrase empty
- **Disconnect button**: `TextButton` "Disconnect" to allow user to bail without authenticating
- **Error display**: `Text` with `MaterialTheme.colorScheme.error` color

---

## LockdownSessionStatus (session info row)

```kotlin
/**
 * Displays current session token TTL information in Security settings.
 * Only visible when node is in UNLOCKED state.
 *
 * @param session Active session info (boots remaining, expiry)
 */
@Composable
fun LockdownSessionStatus(
    session: LockdownState.Unlocked,
)
```

### Display Format

| Condition | Displayed Text |
|-----------|---------------|
| `bootsRemaining > 0 && validUntilEpoch > 0` | "Session: N reboots remaining, expires [formatted date]" |
| `bootsRemaining > 0 && validUntilEpoch == 0` | "Session: N reboots remaining, no time limit" |
| `bootsRemaining == 0 && validUntilEpoch > 0` | "Session: expires [formatted date]" |
| `bootsRemaining == 0 && validUntilEpoch == 0` | "Session: no expiry configured" |

---

## LockNowButton

```kotlin
/**
 * "Lock Now" button for Security settings. Only enabled when the node is
 * UNLOCKED and lockdown is applicable.
 *
 * @param isEnabled true when node is unlocked and user can issue lock-now
 * @param onClick Callback to trigger lock-now via LockdownCoordinator
 */
@Composable
fun LockNowButton(
    isEnabled: Boolean,
    onClick: () -> Unit,
)
```

### Visibility Rules

| Coordinator State | Button State |
|-------------------|-------------|
| `NotApplicable` | Hidden (node doesn't support lockdown) |
| `Unlocked` | Visible + Enabled |
| `Locked` / `NeedsProvision` | Visible + Disabled with "Device is locked" hint |
| `LockNowPending` | Visible + Disabled + "Locking..." text |
| `LockNowAcknowledged` | Hidden (disconnecting) |

---

## Integration Point

The `LockdownScreen` composable is placed at the app's top-level composition:

```kotlin
// In the main app content composable (after connection established):
val lockdownState by lockdownCoordinator.state.collectAsStateWithLifecycle()

Box {
    // Normal navigation content
    MeshtasticNavDisplay(...)
    
    // Lockdown overlay — blocks everything when active
    when (val state = lockdownState) {
        is LockdownState.NotApplicable,
        is LockdownState.Unlocked -> { /* Normal operation, no overlay */ }
        else -> {
            LockdownScreen(
                state = state,
                onSubmitPassphrase = { pass, boots, epoch ->
                    scope.launch { lockdownCoordinator.submitPassphrase(pass, boots, epoch) }
                },
                onDisconnect = { connectionManager.disconnect() },
            )
        }
    }
}
```
