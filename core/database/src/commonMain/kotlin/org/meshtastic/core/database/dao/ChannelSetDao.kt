/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import okio.ByteString
import org.meshtastic.core.database.entity.ChannelSetEntity

/** Per-device persistence of the connected device's [ChannelSetEntity]. */
@Dao
interface ChannelSetDao {

    /** Observes the single stored channel set, emitting null when none has been persisted for this device yet. */
    @Query("SELECT * FROM channel_set WHERE id = 0 LIMIT 1")
    fun observe(): Flow<ChannelSetEntity?>

    /** One-shot read of the stored channel set for read-modify-write updates; null when none is persisted yet. */
    @Query("SELECT * FROM channel_set WHERE id = 0 LIMIT 1")
    suspend fun get(): ChannelSetEntity?

    // Deliberately @Insert(onConflict = REPLACE), not @Upsert: on this entity, Room's generated @Upsert UPDATE path
    // throws a bare android.database.SQLException under Robolectric's androidHostTest SQLite implementation on every
    // write past the first (verified: 4/8 SwitchingChannelSetDataSourceTest cases failed, all and only the ones that
    // write to an already-existing row -- pure-JVM jvmTest and on-device runs are unaffected). REPLACE is a single
    // INSERT OR REPLACE statement with no separate UPDATE path, so it doesn't hit this incompatibility.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChannelSetEntity)

    @Query("DELETE FROM channel_set")
    suspend fun clear()

    /**
     * Drops the cached channel set but keeps [ChannelSetEntity.lastReconciled].
     *
     * The handshake clears the cache before re-downloading the radio's channels. The stored conversations have not
     * moved just because the cache was dropped, so the reconciliation baseline has to outlive it — otherwise every
     * reconnect would look like "no baseline yet" and a channel replaced while the app was away would never be noticed.
     */
    @Query("UPDATE channel_set SET channel_set = :emptyChannelSet WHERE id = 0")
    suspend fun clearRetainingBaseline(emptyChannelSet: ByteArray)

    /** Records the channel set the stored conversations are now keyed against. */
    @Query("UPDATE channel_set SET last_reconciled = :baseline WHERE id = 0")
    suspend fun setLastReconciled(baseline: ByteString)
}
