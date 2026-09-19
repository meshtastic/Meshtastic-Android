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
package org.meshtastic.core.data.datasource

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.ChannelSetEntity
import org.meshtastic.core.database.retryOnDbPoolFailure
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.util.planChannelReconciliation
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config

/**
 * Per-device channel-set storage backed by the current device's Room database.
 *
 * Replaces the former single global `channel_set.pb` DataStore. The channel set now switches with
 * [DatabaseProvider.currentDb], exactly like messages and nodes, so switching between devices can no longer render one
 * device's conversations against another device's channels (#4623).
 *
 * The whole [ChannelSet] proto is stored in one row (see [ChannelSetEntity]); mutations are read-modify-write, so they
 * are serialized through [writeMutex] to preserve the atomicity the old DataStore's `updateData {}` provided (the
 * handshake fires overlapping [updateChannelSettings] calls as it downloads channels one by one).
 */
@Single
class SwitchingChannelSetDataSource(
    private val dbManager: DatabaseProvider,
    private val dispatchers: CoroutineDispatchers,
) {
    private val writeMutex = Mutex()

    val channelSetFlow: Flow<ChannelSet> =
        dbManager
            .observeCurrentDb { db -> db.channelSetDao().observe() }
            .retryOnDbPoolFailure("channelSet")
            .map { entity -> entity?.channelSet ?: ChannelSet.Builder().build() }
            .distinctUntilChanged()

    suspend fun clearChannelSet() {
        // Must take writeMutex like mutate() -- otherwise a concurrent mutate() can read the row before this delete,
        // then upsert it back afterwards, silently undoing the clear.
        withContext(dispatchers.io) {
            writeMutex.withLock {
                dbManager.withDb { db ->
                    val dao = db.channelSetDao()
                    val existing = dao.get()
                    val empty = ChannelSet.ADAPTER.encode(ChannelSet.Builder().build())
                    when {
                        existing == null -> Unit

                        // Keep the baseline: the messages did not move because the cache was dropped.
                        existing.lastReconciled != null -> dao.clearRetainingBaseline(empty)

                        // No baseline yet, but a cached set from before this column existed. Adopt it on the way out,
                        // so the first handshake after upgrading can still tell whether the channels changed while
                        // the app was away instead of silently accepting whatever the radio now reports.
                        existing.channelSet.settings.isNotEmpty() -> {
                            dao.setLastReconciled(ChannelSetEntity.encodeBaseline(existing.channelSet))
                            dao.clearRetainingBaseline(empty)
                        }

                        else -> dao.clear()
                    }
                }
            }
        }
    }

    /** Replaces all [ChannelSettings] in a single atomic operation. */
    suspend fun replaceAllSettings(settingsList: List<ChannelSettings>) {
        updateChannelSet(settingsList = settingsList, loraConfig = null)
    }

    /**
     * Atomically updates supplied [ChannelSet] fields while preserving fields omitted by the caller.
     *
     * Conversations are reconciled in the same lock, but **only when [settingsList] is supplied**. A caller that passes
     * the whole channel list is stating a complete set — a manual channel edit, a scanned channel URL, a profile
     * install — which is the only safe moment to decide a channel has gone. A LoRa-only write is not: the radio sends
     * its LoRa config during the handshake, before any channel has arrived, so reconciling there would see an empty
     * channel list and archive every conversation on the device on every reconnect. Those changes are picked up by
     * [reconcileConversations] once the handshake completes, which a preset change always triggers because the radio
     * reboots.
     */
    suspend fun updateChannelSet(settingsList: List<ChannelSettings>?, loraConfig: Config.LoRaConfig?) {
        mutate(reconcile = settingsList != null) { current ->
            current
                .newBuilder()
                .also { wb ->
                    wb.settings = settingsList ?: current.settings
                    wb.lora_config = loraConfig ?: current.lora_config
                }
                .build()
        }
    }

    /**
     * Re-keys stored conversations onto the current channel set, and records it as the new baseline.
     *
     * Conversations are keyed by channel index, so a slot that changes occupant shows the previous channel's history
     * under the new channel until this runs. Idempotent: a second call with nothing changed does nothing.
     *
     * Called explicitly after the handshake because the handshake streams channels in one slot at a time (and its LoRa
     * config arrives as its own write). Reconciling on each of those would see a channel momentarily missing, retire
     * it, and then see it come back.
     */
    suspend fun reconcileConversations() {
        withContext(dispatchers.io) { writeMutex.withLock { dbManager.withDb { db -> db.reconcile() } } }
    }

    /** Places [channel]'s settings at its index, resizing with blank channels to fill any gap (parity with legacy). */
    suspend fun updateChannelSettings(channel: Channel) {
        if (channel.role == Channel.Role.DISABLED) return
        mutate { current ->
            val settings = current.settings.toMutableList()
            while (settings.size <= channel.index) {
                settings.add(ChannelSettings.Builder().build())
            }
            settings[channel.index] = channel.settings ?: ChannelSettings.Builder().build()
            current.newBuilder().also { wb -> wb.settings = settings }.build()
        }
    }

    suspend fun setLoraConfig(config: Config.LoRaConfig) {
        updateChannelSet(settingsList = null, loraConfig = config)
    }

    private suspend fun mutate(reconcile: Boolean = false, transform: (ChannelSet) -> ChannelSet) {
        withContext(dispatchers.io) {
            writeMutex.withLock {
                dbManager.withDb { db ->
                    val dao = db.channelSetDao()
                    val existing = dao.get()
                    val current = existing?.channelSet ?: ChannelSet.Builder().build()
                    dao.upsert(
                        ChannelSetEntity(channelSet = transform(current), lastReconciled = existing?.lastReconciled),
                    )
                    if (reconcile) db.reconcile()
                }
            }
        }
    }

    /**
     * Runs one reconciliation pass against the stored channel set. Caller holds [writeMutex].
     *
     * The re-key and the new baseline commit together. Apply-then-record as two transactions would, on a crash between
     * them, recompute the identical plan next time and apply it to rows that had already moved — swapping a pair of
     * channels back, or sweeping the *new* occupant's messages into the previous one's archive.
     */
    private suspend fun MeshtasticDatabase.reconcile() {
        useWriterConnection { transactor ->
            transactor.immediateTransaction {
                val dao = channelSetDao()
                val entity = dao.get() ?: return@immediateTransaction
                val current = entity.channelSet
                val baseline = entity.lastReconciledChannelSet
                // No baseline yet (fresh install, or upgrading from before this existed): adopt the current set as
                // the starting point. Messages already misfiled by an earlier switch stay where they are -- there is
                // nothing recorded to tell us where they belonged.
                if (baseline != null) {
                    val changes =
                        planChannelReconciliation(
                            old = baseline,
                            new = current,
                            retiredTokens = packetDao().getRetiredContactTokens(),
                        )
                    if (changes.isNotEmpty()) {
                        Logger.i { "Re-keying ${changes.size} conversation(s) after a channel-set change" }
                        packetDao().applyChannelReconciliation(changes)
                    }
                }
                dao.setLastReconciled(ChannelSetEntity.encodeBaseline(current))
            }
        }
    }
}
