# Contract: LockdownPassphraseStore

**Module**: `core/repository` (interface) / `core/datastore` (platform implementations)  
**Source set**: `commonMain` (interface), `androidMain` / `jvmMain` / `iosMain` (implementations)

## Interface

```kotlin
package org.meshtastic.core.repository

/**
 * Encrypted per-node passphrase cache for lockdown auto-replay.
 *
 * Implementations MUST store passphrases using platform-appropriate
 * encryption (EncryptedSharedPreferences on Android, Keychain on iOS,
 * KeyStore-backed file on JVM). Passphrase bytes MUST NOT appear in
 * logs, crash reports, or unencrypted storage.
 */
interface LockdownPassphraseStore {

    /**
     * Retrieve the cached passphrase for a node.
     * @param nodeId Mesh node number
     * @return Raw passphrase bytes, or null if none cached
     */
    suspend fun get(nodeId: Int): ByteArray?

    /**
     * Store a passphrase for a node, overwriting any previous value.
     * @param nodeId Mesh node number
     * @param passphrase Raw passphrase bytes (1-32)
     */
    suspend fun put(nodeId: Int, passphrase: ByteArray)

    /**
     * Remove the cached passphrase for a node.
     * @param nodeId Mesh node number
     */
    suspend fun clear(nodeId: Int)
}
```

## Platform Implementations

### Android (`androidMain`)

```kotlin
@Single
class LockdownPassphraseStoreImpl(
    private val context: Context,
) : LockdownPassphraseStore {
    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            "lockdown_passphrases",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun get(nodeId: Int): ByteArray? =
        prefs.getString(nodeId.toKey(), null)?.let { Base64.decode(it) }

    override suspend fun put(nodeId: Int, passphrase: ByteArray) =
        prefs.edit().putString(nodeId.toKey(), Base64.encode(passphrase)).apply()

    override suspend fun clear(nodeId: Int) =
        prefs.edit().remove(nodeId.toKey()).apply()

    private fun Int.toKey(): String = "lockdown_${toUInt().toString(16)}"
}
```

### JVM / iOS (stubs)

```kotlin
@Single
class LockdownPassphraseStoreImpl : LockdownPassphraseStore {
    // No-op: passphrase never cached on this platform.
    // User is always prompted on reconnection.
    override suspend fun get(nodeId: Int): ByteArray? = null
    override suspend fun put(nodeId: Int, passphrase: ByteArray) { /* no-op */ }
    override suspend fun clear(nodeId: Int) { /* no-op */ }
}
```

## Behavioral Contract

1. **Encryption at rest**: Android impl MUST use AES-256-GCM via EncryptedSharedPreferences. Passphrase bytes are Base64-encoded for SharedPreferences string storage.
2. **Key format**: `"lockdown_${nodeId.toUInt().toString(16)}"` — hex representation avoids negative-int issues.
3. **No logging**: Implementations MUST NOT log passphrase content or full node addresses.
4. **Thread safety**: `SharedPreferences.edit().apply()` is async-safe on Android. Suspend modifier allows IO dispatcher usage.
5. **Lifecycle**: Store persists across app restarts. Cleared only on explicit `clear()` call (auth failure) or app data wipe.
6. **Stubs**: JVM/iOS stubs are intentionally no-op. This means auto-replay won't work on those platforms until real implementations are added. This is acceptable per spec (Android is primary target).
