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
package org.meshtastic.core.data.repository

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.data.datasource.SignalSurveyLocalDataSource
import org.meshtastic.core.database.entity.SignalSurveySessionEntity
import org.meshtastic.core.database.entity.asEntity
import org.meshtastic.core.database.entity.asExternalModel
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.SignalSample
import org.meshtastic.core.model.SignalSurveySession
import org.meshtastic.core.model.util.isDirectReception
import org.meshtastic.core.model.util.isLiveLoraReception
import org.meshtastic.core.model.util.snrOrNull
import org.meshtastic.core.repository.PhoneLocation
import org.meshtastic.core.repository.PhoneLocationProvider
import org.meshtastic.core.repository.PhoneLocationState
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.SignalSurveyBlocker
import org.meshtastic.core.repository.SignalSurveyRepository
import org.meshtastic.core.repository.isTcp
import org.meshtastic.proto.MeshPacket
import kotlin.time.Clock

@Single(binds = [SignalSurveyRepository::class])
class SignalSurveyRepositoryImpl(
    private val serviceRepository: ServiceRepository,
    private val phoneLocationProvider: PhoneLocationProvider,
    private val radioPrefs: RadioPrefs,
    private val radioConfigRepository: RadioConfigRepository,
    private val local: SignalSurveyLocalDataSource,
    private val clock: Clock,
    scope: ServiceScope,
) : SignalSurveyRepository {

    private val _recording = MutableStateFlow(false)
    override val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _blocker = MutableStateFlow<SignalSurveyBlocker?>(null)
    override val blocker: StateFlow<SignalSurveyBlocker?> = _blocker.asStateFlow()

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    override val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    override val sessions: Flow<List<SignalSurveySession>> =
        local.sessions().map { rows -> rows.map { it.asExternalModel() } }

    init {
        // One collector owns the recorder, so a start that races a stop cannot leave a recorder running unowned.
        scope.launch { _recording.collectLatest { recording -> if (recording) record() } }
    }

    override fun start() {
        _recording.value = true
    }

    override fun stop() {
        _recording.value = false
    }

    override fun samples(sessionId: Long): Flow<List<SignalSample>> =
        local.samples(sessionId).map { rows -> rows.map { it.asExternalModel() } }

    override suspend fun samplesOnce(sessionId: Long): List<SignalSample> =
        local.samplesOnce(sessionId).map { it.asExternalModel() }

    override suspend fun delete(sessionId: Long) = local.deleteSession(sessionId)

    private suspend fun record() {
        // The preset is kept with the session because an SNR reading only means something against it.
        val lora = radioConfigRepository.localConfigFlow.firstOrNull()?.lora
        val preset = lora?.takeIf { it.use_preset }?.modem_preset?.name
        val session = SignalSurveySessionEntity(startedAt = now(), modemPreset = preset)
        val sessionId = safeCatching { local.openSession(session) }.getOrNull()
        if (sessionId == null) {
            _recording.value = false
            return
        }
        _activeSessionId.value = sessionId
        try {
            log(sessionId)
        } finally {
            _blocker.value = null
            _activeSessionId.value = null
            withContext(NonCancellable) { safeCatching { local.closeSession(sessionId, endedAt = now()) } }
        }
    }

    /** Stores a reading for each live reception until cancelled. */
    private suspend fun log(sessionId: Long) = coroutineScope {
        val location = locationStates().stateIn(this, SharingStarted.Eagerly, null)
        val connectedAt = MutableStateFlow<Long?>(null)
        launch {
            combine(location.filterNotNull(), serviceRepository.connectionState, radioPrefs.devAddr) {
                    state,
                    connection,
                    _,
                ->
                blockerFor(state, connection)
            }
                .collect { _blocker.value = it }
        }
        launch {
            serviceRepository.connectionState.collect { state ->
                connectedAt.value = now().takeIf { state is ConnectionState.Connected }
            }
        }
        // The emitter suspends on a full buffer, and that would hold up every other packet collector behind a slow
        // database write. A survey can lose a reading; the pipeline cannot wait for one.
        serviceRepository.meshPacketFlow.buffer(PACKET_BUFFER, BufferOverflow.DROP_OLDEST).collect { packet ->
            val now = now()
            val settled = connectedAt.value?.let { now - it >= CONNECT_SETTLE_MS } == true
            val sample =
                if (settled && !radioPrefs.isTcp()) surveySample(packet, location.value?.location, now) else null
            if (sample != null) safeCatching { local.insert(sample.asEntity(sessionId)) }
        }
    }

    /**
     * The provider's flow ends at once when permission is missing, so it is asked again until it stays open. That is
     * what lets a survey started before the grant begin logging after it.
     */
    private fun locationStates(): Flow<PhoneLocationState> = flow {
        while (true) {
            emitAll(phoneLocationProvider.locationUpdates())
            delay(LOCATION_RETRY_MS)
        }
    }

    private fun blockerFor(state: PhoneLocationState, connection: ConnectionState): SignalSurveyBlocker? = when {
        radioPrefs.isTcp() -> SignalSurveyBlocker.REMOTE_RADIO
        connection !is ConnectionState.Connected -> SignalSurveyBlocker.NOT_CONNECTED
        !state.permissionGranted -> SignalSurveyBlocker.LOCATION_PERMISSION
        !state.providerEnabled -> SignalSurveyBlocker.LOCATION_OFF
        !state.hasFix -> SignalSurveyBlocker.NO_FIX
        else -> null
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()

    private companion object {
        const val PACKET_BUFFER = 256
        const val LOCATION_RETRY_MS = 2_000L

        // Firmware hands over what it queued while disconnected right after the handshake. A radio with no clock
        // leaves those packets unstamped, so their age cannot rule them out.
        const val CONNECT_SETTLE_MS = 15_000L
    }
}

internal const val SURVEY_MAX_FIX_AGE_MS = 15_000L
internal const val SURVEY_MAX_FIX_ACCURACY_M = 50f
private const val MS_PER_SECOND = 1000L

/** The reading [packet] yields at [fix], or null when the packet is not a live reception or the fix is too poor. */
internal fun surveySample(packet: MeshPacket, fix: PhoneLocation?, nowMillis: Long): SignalSample? {
    val rssi = packet.rx_rssi
    val usableFix = fix?.takeIf { it.isUsableAt(nowMillis) }
    return if (rssi == null || usableFix == null || !packet.isLiveLoraReception(nowMillis / MS_PER_SECOND)) {
        null
    } else {
        SignalSample(
            timeMillis = nowMillis,
            latitude = usableFix.latitude,
            longitude = usableFix.longitude,
            accuracyM = usableFix.accuracyM,
            fromNum = packet.from,
            rssi = rssi,
            snr = packet.snrOrNull(),
            direct = packet.isDirectReception(),
        )
    }
}

/** Recent enough to stand for where the phone is now. A fix that reports no accuracy is taken as it comes. */
private fun PhoneLocation.isUsableAt(nowMillis: Long): Boolean {
    val accuracy = accuracyM
    return nowMillis - timeMillis <= SURVEY_MAX_FIX_AGE_MS &&
        (accuracy == null || accuracy <= SURVEY_MAX_FIX_ACCURACY_M)
}
