# Contract: LockdownPassphraseStore

**Module**: `core/repository` (interface) / `core/service` (platform implementations)  
**Source set**: `commonMain` (interface), `androidMain` / `jvmMain` (implementations)

## Interface

```kotlin
package org.meshtastic.core.repository

/** Stored passphrase entry with associated TTL parameters. */
data class StoredPassphrase(val passphrase: String, val boots: Int, val hours: Int)

/**
 * Encrypted per-device storage for lockdown passphrases.
 *
 * Platform implementations should use secure storage (e.g., EncryptedSharedPreferences
 * on Android, KeyStore-backed AES-GCM on Desktop). Passphrase access is NOT gated
 * behind biometric authentication so that auto-unlock can run in the background
 * without user interaction.
 */
interface LockdownPassphraseStore {
    /** Retrieves the stored passphrase for the given device address, or null if not stored. */
    fun getPassphrase(deviceAddress: String): StoredPassphrase?

    /** Saves the passphrase and TTL parameters for the given device address. */
    fun savePassphrase(deviceAddress: String, passphrase: String, boots: Int, hours: Int)

    /** Clears the stored passphrase for the given device address. */
    fun clearPassphrase(deviceAddress: String)

    companion object {
        const val DEFAULT_BOOTS = 50
    }
}
```

## Platform Implementations

### Android (`core/service/androidMain`)

```kotlin
@Single(binds = [LockdownPassphraseStore::class])
class LockdownPassphraseStoreImpl(app: Application) : LockdownPassphraseStore {
    private val prefs: SharedPreferences? by lazy {
        try {
            val masterKey = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(app, PREFS_FILE_NAME, masterKey, ...)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to initialize encrypted passphrase store" }
            null
        }
    }
    private fun requirePrefs(): SharedPreferences = prefs ?: error("Encrypted passphrase store unavailable")
}
```

- **Storage**: `EncryptedSharedPreferences` with AES-256-GCM MasterKey (hardware keystore when available)
- **Key format**: `"${sanitizedDeviceAddress}_passphrase"`, `"..._boots"`, `"..._hours"`
- **Error resilience**: initialization failures are logged once; subsequent operations fail fast so callers can handle persistence errors explicitly

### JVM/Desktop (`core/service/jvmMain`)

```kotlin
@Single(binds = [LockdownPassphraseStore::class])
class LockdownPassphraseStoreImpl : LockdownPassphraseStore {
    private val masterKey: SecretKey? by lazy { loadOrCreateMasterKey() }
    // AES-256-GCM encryption per device entry
}
```

- **Storage**: PKCS12 KeyStore at `$MESHTASTIC_DATA_DIR/lockdown/keystore.p12` (default `~/.meshtastic/lockdown/keystore.p12`) + per-device `.enc` files
- **Key management**: Generates random AES-256 key on first use, stores in PKCS12 keystore
- **Encryption**: AES-256-GCM with random IV per write; format `[1B IV len][IV][ciphertext]`
- **Data format**: Line-based `"boots\nhours\npassphrase"` (avoids kotlinx-serialization dependency)
- **Error resilience**: read failures return `null`; write failures throw so the coordinator can log and keep the session unlocked

## Behavioral Contract

1. **Encryption at rest**: Both platforms encrypt passphrase data. Android via EncryptedSharedPreferences, Desktop via AES-256-GCM with KeyStore-managed key.
2. **Key format**: Device addresses are sanitized for file/key safety.
3. **No logging**: Implementations MUST NOT log passphrase content or full device addresses.
4. **Thread safety**: Android `SharedPreferences.edit().apply()` is async-safe. JVM file I/O is synchronous (called from single-threaded radio dispatcher).
5. **Lifecycle**: Store persists across app restarts. Cleared only on explicit `clearPassphrase()` call (auth failure) or app data wipe.
6. **DEFAULT_BOOTS**: Companion constant (50) is the shared default for provisioning and cached TTL metadata.
