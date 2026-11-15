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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.data.datasource.SignalSurveyLocalDataSource
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.SignalSurveySession
import org.meshtastic.core.repository.PhoneLocation
import org.meshtastic.core.repository.PhoneLocationProvider
import org.meshtastic.core.repository.PhoneLocationState
import org.meshtastic.core.repository.SignalSurveyBlocker
import org.meshtastic.core.testing.FakeDatabaseProvider
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioPrefs
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.MeshPacket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class SignalSurveyRepositoryImplTest {

    // Real dispatchers and runBlocking, like the other Room-backed repository tests here.
    private val unconfined = Dispatchers.Unconfined
    private val dispatchers = CoroutineDispatchers(main = unconfined, io = unconfined, default = unconfined)
    private val scope = CoroutineScope(SupervisorJob() + unconfined)

    private val service = FakeServiceRepository()
    private val location = MutableStateFlow(PhoneLocationState(permissionGranted = true, providerEnabled = true))
    private val radioPrefs = FakeRadioPrefs()
    private val radioConfig = FakeRadioConfigRepository()
    private val db = FakeDatabaseProvider()
    private var clock = 1_700_000_000_000L

    /** Swapped by a test that needs the provider's flow to end, as Android's does without permission. */
    private var locationFlow: () -> Flow<PhoneLocationState> = { location }

    private val repository =
        SignalSurveyRepositoryImpl(
            serviceRepository = service,
            phoneLocationProvider =
            object : PhoneLocationProvider {
                override fun locationUpdates(): Flow<PhoneLocationState> = locationFlow()
            },
            radioPrefs = radioPrefs,
            radioConfigRepository = radioConfig,
            local = SignalSurveyLocalDataSource(db, dispatchers),
            clock =
            object : Clock {
                override fun now() = Instant.fromEpochMilliseconds(clock)
            },
            scope = scope.asServiceScope(),
        )

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun fixNow() {
        location.value =
            PhoneLocationState(
                permissionGranted = true,
                providerEnabled = true,
                location =
                PhoneLocation(
                    latitude = 30.0,
                    longitude = -95.0,
                    altitude = null,
                    timeMillis = clock,
                    accuracyM = 5f,
                ),
            )
    }

    private fun heardNow() = MeshPacket.Builder()
        .also { wb ->
            wb.from = 0x1234
            wb.rx_time = (clock / 1000).toInt()
            wb.rx_rssi = -101
            wb.rx_snr = 4.5f
            wb.transport_mechanism = MeshPacket.TransportMechanism.TRANSPORT_LORA
            wb.hop_start = 3
            wb.hop_limit = 2
        }
        .build()

    /** Connects, then moves the clock past the window in which firmware hands over what it queued. */
    private suspend fun startSettled() {
        service.setConnectionState(ConnectionState.Connected)
        startRecording()
        clock += 20.seconds.inWholeMilliseconds
        fixNow()
    }

    /**
     * Opening a session is a database write, so the recorder is not listening the instant [start] returns. The packet
     * flow has no replay, and a packet sent before the recorder subscribes is lost without a trace.
     */
    private suspend fun startRecording() {
        repository.start()
        withTimeout(10.seconds) { repository.activeSessionId.first { it != null } }
        delay(RECORDER_SETTLE)
    }

    /** The blocker once the recorder has had time to react to the last change. */
    private suspend fun settledBlocker(): SignalSurveyBlocker? {
        delay(RECORDER_SETTLE)
        return repository.blocker.value
    }

    /** Waits until the sessions hold [count] readings between them, and returns them newest first. */
    private suspend fun awaitReadings(count: Int): List<SignalSurveySession> =
        withTimeout(10.seconds) { repository.sessions.first { all -> all.sumOf { it.readingCount } == count } }

    private suspend fun awaitNoSessions() = withTimeout(10.seconds) { repository.sessions.first { it.isEmpty() } }

    private suspend fun allSamples() = repository.sessions.first().flatMap { repository.samplesOnce(it.id) }

    @Test
    fun `a reception is stored in the open session with the phone position and the packet signal`() = runBlocking {
        startSettled()

        service.emitMeshPacket(heardNow())
        val session = awaitReadings(1).single()

        assertEquals(session.id, repository.activeSessionId.value)
        val sample = repository.samplesOnce(session.id).single()
        assertEquals(30.0, sample.latitude)
        assertEquals(-95.0, sample.longitude)
        assertEquals(0x1234, sample.fromNum)
        assertEquals(-101, sample.rssi)
        assertEquals(4.5f, sample.snr)
        assertFalse(sample.direct)
    }

    @Test
    fun `nothing is stored until recording starts or after it stops`() = runBlocking {
        service.setConnectionState(ConnectionState.Connected)
        fixNow()
        service.emitMeshPacket(heardNow())

        startSettled()
        service.emitMeshPacket(heardNow())
        awaitReadings(1)
        repository.stop()
        service.emitMeshPacket(heardNow())
        delay(SETTLE_FOR_NO_WRITE)

        assertEquals(1, allSamples().size)
        assertFalse(repository.recording.value)
        assertNull(repository.activeSessionId.value)
    }

    @Test
    fun `each start opens its own session and stop closes it`() = runBlocking {
        startSettled()
        service.emitMeshPacket(heardNow())
        awaitReadings(1)
        repository.stop()

        clock += 60.seconds.inWholeMilliseconds
        startRecording()
        clock += 20.seconds.inWholeMilliseconds
        fixNow()
        service.emitMeshPacket(heardNow())
        val sessions = awaitReadings(2)

        assertEquals(listOf(1, 1), sessions.map { it.readingCount })
        // Newest first: the open session has no end, the earlier one was closed by stop.
        assertNull(sessions[0].endedAtMillis)
        assertNotNull(sessions[1].endedAtMillis)
        assertTrue(sessions[0].startedAtMillis > sessions[1].startedAtMillis)
    }

    @Test
    fun `a session that logged nothing leaves no history`() = runBlocking {
        startSettled()
        repository.stop()

        assertTrue(awaitNoSessions().isEmpty())
    }

    @Test
    fun `a session remembers the preset it was recorded on`() = runBlocking {
        val lora =
            Config.LoRaConfig.Builder()
                .also { wb ->
                    wb.use_preset = true
                    wb.modem_preset = Config.LoRaConfig.ModemPreset.SHORT_FAST
                }
                .build()
        radioConfig.setLocalConfigDirect(LocalConfig.Builder().also { wb -> wb.lora = lora }.build())
        startSettled()
        service.emitMeshPacket(heardNow())

        assertEquals("SHORT_FAST", awaitReadings(1).single().modemPreset)
    }

    @Test
    fun `a session on custom lora settings records no preset`() = runBlocking {
        startSettled()
        service.emitMeshPacket(heardNow())

        assertNull(awaitReadings(1).single().modemPreset)
    }

    @Test
    fun `deleting a session removes it and its readings`() = runBlocking {
        startSettled()
        service.emitMeshPacket(heardNow())
        val session = awaitReadings(1).single()
        repository.stop()

        repository.delete(session.id)

        assertTrue(awaitNoSessions().isEmpty())
        assertTrue(repository.samplesOnce(session.id).isEmpty())
    }

    @Test
    fun `the readings of a session are re-emitted as it grows`() = runBlocking {
        startSettled()
        service.emitMeshPacket(heardNow())
        val session = awaitReadings(1).single()

        service.emitMeshPacket(heardNow())

        assertEquals(2, withTimeout(10.seconds) { repository.samples(session.id).first { it.size == 2 } }.size)
    }

    @Test
    fun `packets handed over right after connecting are skipped`() = runBlocking {
        service.setConnectionState(ConnectionState.Connected)
        startRecording()
        fixNow()

        service.emitMeshPacket(heardNow())
        delay(SETTLE_FOR_NO_WRITE)

        assertEquals(0, allSamples().size)
    }

    @Test
    fun `a radio reached over the network logs nothing and says why`() = runBlocking {
        radioPrefs.setDevAddr("${InterfaceId.TCP.id}192.168.1.5")
        startSettled()

        service.emitMeshPacket(heardNow())
        delay(SETTLE_FOR_NO_WRITE)

        assertEquals(0, allSamples().size)
        assertEquals(SignalSurveyBlocker.REMOTE_RADIO, settledBlocker())
    }

    @Test
    fun `the blocker names the missing location piece and clears on stop`() = runBlocking {
        service.setConnectionState(ConnectionState.Connected)
        location.value = PhoneLocationState(permissionGranted = false, providerEnabled = true)
        startRecording()
        assertEquals(SignalSurveyBlocker.LOCATION_PERMISSION, settledBlocker())

        location.value = PhoneLocationState(permissionGranted = true, providerEnabled = false)
        assertEquals(SignalSurveyBlocker.LOCATION_OFF, settledBlocker())

        location.value = PhoneLocationState(permissionGranted = true, providerEnabled = true)
        assertEquals(SignalSurveyBlocker.NO_FIX, settledBlocker())

        fixNow()
        assertNull(settledBlocker())

        location.value = PhoneLocationState(permissionGranted = false, providerEnabled = true)
        repository.stop()
        assertNull(settledBlocker())
    }

    @Test
    fun `a survey with no radio connected says so`() = runBlocking {
        fixNow()
        startRecording()

        assertEquals(SignalSurveyBlocker.NOT_CONNECTED, settledBlocker())

        service.setConnectionState(ConnectionState.Connected)
        assertNull(settledBlocker())
    }

    @Test
    fun `a survey started before location access begins logging once it is granted`() = runBlocking {
        var granted = false
        locationFlow = {
            if (granted) location else flowOf(PhoneLocationState(permissionGranted = false, providerEnabled = false))
        }
        startSettled()
        assertEquals(SignalSurveyBlocker.LOCATION_PERMISSION, settledBlocker())

        granted = true
        withTimeout(10.seconds) { repository.blocker.first { it == null } }
        service.emitMeshPacket(heardNow())

        assertEquals(1, awaitReadings(1).single().readingCount)
    }

    @Test
    fun `starting twice and stopping once leaves the survey off`() = runBlocking {
        startSettled()
        startRecording()
        repository.stop()

        service.emitMeshPacket(heardNow())
        delay(SETTLE_FOR_NO_WRITE)

        assertEquals(0, allSamples().size)
        assertFalse(repository.recording.value)
    }

    @Test
    fun `a stale or coarse fix yields no reading`() {
        val now = clock
        val packet = heardNow()
        val fresh = PhoneLocation(latitude = 1.0, longitude = 2.0, altitude = null, timeMillis = now, accuracyM = 5f)

        assertEquals(-101, surveySample(packet, fresh, now)?.rssi)
        assertNull(surveySample(packet, null, now))
        assertNull(surveySample(packet, fresh.copy(timeMillis = now - SURVEY_MAX_FIX_AGE_MS - 1), now))
        assertNull(surveySample(packet, fresh.copy(accuracyM = SURVEY_MAX_FIX_ACCURACY_M + 1), now))
        assertEquals(-101, surveySample(packet, fresh.copy(accuracyM = null), now)?.rssi)
    }

    private companion object {
        const val SETTLE_FOR_NO_WRITE = 300L
        const val RECORDER_SETTLE = 150L
    }
}
