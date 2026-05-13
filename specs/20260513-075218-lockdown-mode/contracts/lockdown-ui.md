# Contract: Lockdown UI Components

**Module**: `feature/settings`  
**Source set**: `commonMain`

## LockdownDialog

```kotlin
@Composable
fun LockdownDialog(
    lockdownState: LockdownState,
    onSubmit: (passphrase: String, boots: Int, hours: Int) -> Unit,
    onDisconnect: () -> Unit,
)
```

`LockdownDialog` is a non-dismissable `AlertDialog` shown while the connected device requires lockdown authentication. It uses `onDismissRequest = {}` and offers an explicit Disconnect button instead of allowing dismissal.

### Rendered States

| `LockdownState` | UI Rendering |
|-----------------|-------------|
| `NeedsProvision` | "Set Passphrase" title, passphrase + confirm fields, editable `boots` / `hours` inputs, Submit button |
| `Locked` | "Enter Passphrase" title, passphrase field, lock reason when present, Submit button |
| `UnlockFailed` | Same as `Locked` plus incorrect-passphrase error text |
| `UnlockBackoff` | Same as `Locked` plus backoff error text; Submit disabled |
| `None` / `Unlocked` / `LockNowAcknowledged` | Dialog hidden |

### Component Details

- **Passphrase field**: `OutlinedTextField` with password visibility toggle
- **Confirm field**: shown only in provisioning mode
- **Provisioning TTL fields**: integer `boots` and `hours`; current defaults are `50` and `0`
- **Validation**: passphrase is required and limited to 64 UTF-8 bytes; confirm field must match in provisioning mode
- **Disconnect button**: explicit escape hatch when the user does not want to authenticate

## LockdownSessionStatus

```kotlin
@Composable
fun LockdownSessionStatus(tokenInfo: LockdownTokenInfo?, modifier: Modifier = Modifier)
```

`LockdownSessionStatus` is shown in `SecurityConfigScreen` only when `sessionAuthorized == true` and `tokenInfo` is non-null.

### Display Format

| Condition | Displayed Text |
|-----------|---------------|
| `bootsRemaining > 0` | "Session: N reboots remaining" |
| `expiryEpoch > 0` | "expires [formatted date]" |
| `expiryEpoch == 0` | "no time limit" |

## Lock Now Action

There is no standalone `LockNowButton` composable in the current implementation. The Lock Now action is a `NodeActionButton` embedded directly in `SecurityConfigScreen` and enabled only when the device is connected and `sessionAuthorized == true`.

## Integration Points

- `UIViewModel` and `ConnectionsViewModel` expose `lockdownState` from `ServiceRepository`
- `RadioConfigViewModel` exposes `lockdownTokenInfo`, `sessionAuthorized`, and `sendLockNow()` for the security screen
- `SecurityConfigScreen` renders `LockdownSessionStatus` above the Lock Now action when the current session is authorized
