/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.meshtastic.core.database

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages per-device Room database instances for node data, with LRU eviction.
 */
@Singleton
class DatabaseManager
@Inject
constructor(
    private val app: Application,
) {
    val prefs: SharedPreferences = app.getSharedPreferences("db-manager-prefs", Context.MODE_PRIVATE)
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val mutex = Mutex()

    private val _currentDb = MutableStateFlow<MeshtasticDatabase?>(null)
    val currentDb: StateFlow<MeshtasticDatabase> =
        _currentDb
            .filterNotNull()
            .stateIn(managerScope, SharingStarted.Eagerly, buildRoomDb(defaultDbName()))

    private val _currentAddress = MutableStateFlow<String?>(null)
    val currentAddress: StateFlow<String?> = _currentAddress

    private val dbCache = mutableMapOf<String, MeshtasticDatabase>() // key = dbName

    /** Initialize the active database for [address]. */
    suspend fun init(address: String?) = switchActiveDatabase(address)

    /** Switch active database to the one associated with [address]. Serialized via mutex. */
    suspend fun switchActiveDatabase(address: String?) = mutex.withLock {
        val dbName = buildDbName(address)

        // Fast path: no-op if already on this address
        if (_currentAddress.value == address && _currentDb.value != null) {
            markLastUsed(dbName)
            return
        }

        // Build/open Room DB off the main thread
        val db = dbCache[dbName] ?: withContext(Dispatchers.IO) { buildRoomDb(dbName) }.also { dbCache[dbName] = it }

        _currentDb.value = db
        _currentAddress.value = address
        markLastUsed(dbName)

        // Defer LRU eviction so switch is not blocked by filesystem work
        managerScope.launch(Dispatchers.IO) { enforceCacheLimit(activeDbName = dbName) }

        Timber.i("Switched active DB to ${anonymizeDbName(dbName)} for address ${anonymizeAddress(address)}")
    }

    /** Execute [block] with the current DB instance. */
    fun <T> withDb(block: (MeshtasticDatabase) -> T): T = block(currentDb.value)

    private fun buildRoomDb(dbName: String): MeshtasticDatabase =
        Room.databaseBuilder(app.applicationContext, MeshtasticDatabase::class.java, dbName)
            .fallbackToDestructiveMigration(false)
            .build()

    private fun defaultDbName(): String = DatabaseConstants.DEFAULT_DB_NAME

    private fun buildDbName(address: String?): String =
        if (address.isNullOrBlank()) defaultDbName() else "${DatabaseConstants.DB_PREFIX}_${shortSha1(normalizeAddress(address))}"

    private fun normalizeAddress(addr: String?): String = addr?.uppercase()?.replace(":", "") ?: "DEFAULT"

    private fun shortSha1(s: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(10)

    private fun markLastUsed(dbName: String) {
        prefs.edit().putLong(lastUsedKey(dbName), System.currentTimeMillis()).apply()
    }

    private fun lastUsed(dbName: String): Long {
        val k = lastUsedKey(dbName)
        val v = prefs.getLong(k, 0L)
        return if (v == 0L) getDbFile(dbName)?.lastModified() ?: 0L else v
    }

    private fun lastUsedKey(dbName: String) = "db_last_used:$dbName"

    private fun getDbFile(dbName: String): File? = app.getDatabasePath(dbName).takeIf { it.exists() }

    private fun listExistingDbNames(): List<String> {
        val base = app.getDatabasePath(DatabaseConstants.LEGACY_DB_NAME)
        val dir = base.parentFile ?: return emptyList()
        val names = dir.listFiles()?.mapNotNull { f -> f.name } ?: emptyList()
        return names
            .filter { it.startsWith(DatabaseConstants.DB_PREFIX) }
            .filterNot { it.endsWith("-wal") || it.endsWith("-shm") }
            .distinct()
    }

    private fun enforceCacheLimit(activeDbName: String) {
        val limit = prefs.getInt(DatabaseConstants.CACHE_LIMIT_KEY, DatabaseConstants.DEFAULT_CACHE_LIMIT)
            .coerceIn(DatabaseConstants.MIN_CACHE_LIMIT, DatabaseConstants.MAX_CACHE_LIMIT)

        val all = listExistingDbNames()
        if (all.size <= limit) return

        val victims = all
            .filter { it != activeDbName }
            .sortedBy { lastUsed(it) }
            .take(all.size - limit)

        victims.forEach { name ->
            runCatching { dbCache.remove(name)?.close() }
            app.deleteDatabase(name)
            prefs.edit().remove(lastUsedKey(name)).apply()
            Timber.i("Evicted cached DB ${anonymizeDbName(name)}")
        }
    }

    private fun anonymizeAddress(address: String?): String =
        address?.let { it.take(2) + "…" + it.takeLast(2) } ?: "null"

    private fun anonymizeDbName(name: String): String =
        if (name == DatabaseConstants.LEGACY_DB_NAME || name == DatabaseConstants.DEFAULT_DB_NAME) name
        else name.take(DatabaseConstants.DB_PREFIX.length + 1 + 3) + "…"
}

object DatabaseConstants {
    const val DB_PREFIX: String = "meshtastic_database"
    const val LEGACY_DB_NAME: String = DB_PREFIX
    const val DEFAULT_DB_NAME: String = "${DB_PREFIX}_default"

    const val CACHE_LIMIT_KEY: String = "node_db_cache_limit"
    const val DEFAULT_CACHE_LIMIT: Int = 3
    const val MIN_CACHE_LIMIT: Int = 1
    const val MAX_CACHE_LIMIT: Int = 10
}


