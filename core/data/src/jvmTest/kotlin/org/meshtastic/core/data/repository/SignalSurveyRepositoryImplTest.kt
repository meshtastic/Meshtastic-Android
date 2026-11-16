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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.data.datasource.SignalSurveyLocalDataSource
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.SignalSurveySession
import org.meshtastic.core.repository.PhoneLocation
import org.meshtastic.core.repository.PhoneLocationProvider
import org.meshtastic.core.repository.PhoneLocationState
import org.meshtastic.core.repository.SignalSurveyBlocker
import org.meshtastic.core.testing.FakeDatabaseProvider
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioPrefs
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Position
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
    private val nodes = FakeNodeRepository()
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
            radio = SignalSurveyRadio(radioPrefs, radioConfig, nodes),
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

    /** Fails on timeout unless the blocker becomes [expected]. */
    private suspend fun awaitBlocker(expected: SignalSurveyBlocker?) {
        withTimeout(10.seconds) { repository.blocker.first { it == expected } }
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
    fun `a radio reached over the network is placed by its own gps`() = runBlocking {
        radioPrefs.setDevAddr("${InterfaceId.TCP.id}192.168.1.5")
        startSettled()
        nodes.setOurNode(Node(num = 1, position = radioPosition(31.0, Position.LocSource.LOC_INTERNAL)))

        service.emitMeshPacket(heardNow())

        val sample = repository.samplesOnce(awaitReadings(1).single().id).single()
        assertEquals(31.0, sample.latitude, 1e-6)
    }

    @Test
    fun `a radio reached over the network with no position of its own is placed by the phone`() = runBlocking {
        radioPrefs.setDevAddr("${InterfaceId.TCP.id}192.168.1.5")
        startSettled()

        service.emitMeshPacket(heardNow())

        assertEquals(30.0, repository.samplesOnce(awaitReadings(1).single().id).single().latitude)
    }

    @Test
    fun `a radio that reports its own position needs no phone location`() = runBlocking {
        service.setConnectionState(ConnectionState.Connected)
        location.value = PhoneLocationState(permissionGranted = false, providerEnabled = true)
        startRecording()
        awaitBlocker(SignalSurveyBlocker.LOCATION_PERMISSION)

        nodes.setOurNode(Node(num = 1, position = radioPosition(31.0, Position.LocSource.LOC_MANUAL)))
        awaitBlocker(null)
    }

    @Test
    fun `a radio in hand is placed by its fresh gps then by the phone then by its standing fix`() {
        val now = clock
        val phone = PhoneLocation(latitude = 30.0, longitude = -95.0, altitude = null, timeMillis = now, accuracyM = 5f)
        val gps = radioFix(fromGps = true, timeMillis = now)
        val oldGps = gps.copy(timeMillis = now - SURVEY_MAX_FIX_AGE_MS - 1)

        assertEquals(31.0, surveyFix(phone, gps, radioIsRemote = false, now)?.latitude)
        assertEquals(30.0, surveyFix(phone, oldGps, radioIsRemote = false, now)?.latitude)
        assertEquals(30.0, surveyFix(phone, oldGps.copy(still = true), radioIsRemote = false, now)?.latitude)
        assertEquals(31.0, surveyFix(null, oldGps.copy(still = true), radioIsRemote = false, now)?.latitude)
        assertNull(surveyFix(null, oldGps, radioIsRemote = false, now))
    }

    @Test
    fun `a remote radio that reports a position is never placed by the phone`() {
        val now = clock
        val phone = PhoneLocation(latitude = 30.0, longitude = -95.0, altitude = null, timeMillis = now, accuracyM = 5f)
        val moving = radioFix(fromGps = true, timeMillis = now - SURVEY_MAX_FIX_AGE_MS - 1)

        assertNull(surveyFix(phone, moving, radioIsRemote = true, now))
        assertEquals(31.0, surveyFix(phone, moving.copy(still = true), radioIsRemote = true, now)?.latitude)
        assertEquals(30.0, surveyFix(phone, null, radioIsRemote = true, now)?.latitude)
    }

    @Test
    fun `only a position the radio found itself counts as its own`() {
        assertNull(radioPosition(31.0, Position.LocSource.LOC_EXTERNAL).toRadioFix())
        assertNull(radioPosition(31.0, Position.LocSource.LOC_UNSET).toRadioFix())
        assertNull(radioPosition(0.0, Position.LocSource.LOC_INTERNAL, longitude = 0.0).toRadioFix())

        val fixed = assertNotNull(radioPosition(31.0, Position.LocSource.LOC_MANUAL).toRadioFix())
        assertTrue(fixed.still)
        val moving = assertNotNull(radioPosition(31.0, Position.LocSource.LOC_INTERNAL, kmph = 40).toRadioFix())
        assertFalse(moving.still)
        assertTrue(assertNotNull(radioPosition(31.0, Position.LocSource.LOC_INTERNAL, kmph = 0).toRadioFix()).still)
    }

    @Test
    fun `switching to another radio's database ends the survey without writing into it`() = runBlocking {
        startSettled()
        service.emitMeshPacket(heardNow())
        awaitReadings(1)

        db.switchToNewDatabase()
        service.emitMeshPacket(heardNow())

        withTimeout(10.seconds) { repository.recording.first { !it } }
        assertTrue(repository.sessions.first().isEmpty())
    }

    @Test
    fun `a location provider that fails ends the survey so it can be started again`() = runBlocking {
        locationFlow = { flow { throw IllegalStateException("location request failed") } }
        service.setConnectionState(ConnectionState.Connected)
        repository.start()

        withTimeout(10.seconds) { repository.recording.first { !it } }
        locationFlow = { location }
        startRecording()
        assertTrue(repository.recording.value)
    }

    @Test
    fun `the blocker names the missing location piece and clears on stop`() = runBlocking {
        service.setConnectionState(ConnectionState.Connected)
        location.value = PhoneLocationState(permissionGranted = false, providerEnabled = true)
        startRecording()
        awaitBlocker(SignalSurveyBlocker.LOCATION_PERMISSION)

        location.value = PhoneLocationState(permissionGranted = true, providerEnabled = false)
        awaitBlocker(SignalSurveyBlocker.LOCATION_OFF)

        location.value = PhoneLocationState(permissionGranted = true, providerEnabled = true)
        awaitBlocker(SignalSurveyBlocker.NO_FIX)

        fixNow()
        awaitBlocker(null)

        location.value = PhoneLocationState(permissionGranted = false, providerEnabled = true)
        repository.stop()
        awaitBlocker(null)
    }

    @Test
    fun `a survey with no radio connected says so`() = runBlocking {
        fixNow()
        startRecording()

        awaitBlocker(SignalSurveyBlocker.NOT_CONNECTED)

        service.setConnectionState(ConnectionState.Connected)
        awaitBlocker(null)
    }

    @Test
    fun `a survey started before location access begins logging once it is granted`() = runBlocking {
        var granted = false
        locationFlow = {
            if (granted) location else flowOf(PhoneLocationState(permissionGranted = false, providerEnabled = false))
        }
        startSettled()
        awaitBlocker(SignalSurveyBlocker.LOCATION_PERMISSION)

        granted = true
        withTimeout(10.seconds) { repository.blocker.first { it == null } }
        service.emitMeshPacket(heardNow())

        assertEquals(1, awaitReadings(1).single().readingCount)
    }

    @Test
    fun `a second start on a running survey keeps its session`() = runBlocking {
        startSettled()
        val session = repository.activeSessionId.value

        repository.start()
        delay(SETTLE_FOR_NO_WRITE)
        service.emitMeshPacket(heardNow())

        assertEquals(session, repository.activeSessionId.value)
        assertEquals(1, awaitReadings(1).size)
    }

    /**
     * Fails an open survey while holding its session close on a gate, so the test acts during cleanup. Returns the
     * failed session's id and the gate that lets the close finish.
     */
    private suspend fun failSurveyHoldingCleanup(): Pair<Long?, CompletableDeferred<Unit>> {
        val failNow = CompletableDeferred<Unit>()
        val closeGate = CompletableDeferred<Unit>()
        locationFlow = {
            flow {
                failNow.await()
                throw IllegalStateException("location request failed")
            }
        }
        service.setConnectionState(ConnectionState.Connected)
        startRecording()
        val failed = repository.activeSessionId.value
        db.beforeWithDb = { closeGate.await() }
        failNow.complete(Unit)
        withTimeout(10.seconds) { repository.recording.first { !it } }
        withTimeout(10.seconds) { repository.activeSessionId.first { it == null } }
        return failed to closeGate
    }

    @Test
    fun `a start while a failed survey is closing opens a new session`() = runBlocking {
        val (failed, closeGate) = failSurveyHoldingCleanup()

        locationFlow = { location }
        repository.start()
        assertTrue(repository.recording.value)
        closeGate.complete(Unit)

        withTimeout(10.seconds) { repository.activeSessionId.first { it != null && it != failed } }
        assertTrue(repository.recording.value)
    }

    @Test
    fun `a stop while a failed survey is closing leaves it off`() = runBlocking {
        val (_, closeGate) = failSurveyHoldingCleanup()

        repository.stop()
        closeGate.complete(Unit)
        delay(SETTLE_FOR_NO_WRITE)

        assertFalse(repository.recording.value)
        assertNull(repository.activeSessionId.value)
    }

    @Test
    fun `a start right after a stop opens a new session`() = runBlocking {
        startSettled()
        val first = repository.activeSessionId.value

        repository.stop()
        repository.start()

        withTimeout(10.seconds) { repository.activeSessionId.first { it != null && it != first } }
        assertTrue(repository.recording.value)
    }

    @Test
    fun `starting twice and stopping once leaves the survey off`() = runBlocking {
        startSettled()
        val session = repository.activeSessionId.value
        startRecording()
        assertEquals(session, repository.activeSessionId.value)
        repository.stop()

        service.emitMeshPacket(heardNow())
        delay(SETTLE_FOR_NO_WRITE)

        assertEquals(0, allSamples().size)
        assertFalse(repository.recording.value)
        assertNull(repository.activeSessionId.value)
    }

    @Test
    fun `a stale or coarse phone fix yields no reading`() {
        val now = clock
        val packet = heardNow()
        val fresh = PhoneLocation(latitude = 1.0, longitude = 2.0, altitude = null, timeMillis = now, accuracyM = 5f)

        fun sampleAt(fix: PhoneLocation?) = surveySample(packet, surveyFix(fix, null, radioIsRemote = false, now), now)

        assertEquals(-101, sampleAt(fresh)?.rssi)
        assertNull(sampleAt(null))
        assertNull(sampleAt(fresh.copy(timeMillis = now - SURVEY_MAX_FIX_AGE_MS - 1)))
        assertNull(sampleAt(fresh.copy(accuracyM = SURVEY_MAX_FIX_ACCURACY_M + 1)))
        assertEquals(-101, sampleAt(fresh.copy(accuracyM = null))?.rssi)
    }

    private fun radioFix(fromGps: Boolean, timeMillis: Long) =
        RadioFix(latitude = 31.0, longitude = -95.0, timeMillis = timeMillis, still = false, fromGps = fromGps)

    private fun radioPosition(
        latitude: Double,
        source: Position.LocSource,
        longitude: Double = -95.0,
        kmph: Int? = null,
    ) = Position.Builder()
        .also { wb ->
            wb.latitude_i = (latitude * 1e7).toInt()
            wb.longitude_i = (longitude * 1e7).toInt()
            wb.location_source = source
            wb.time = (clock / 1000).toInt()
            wb.ground_speed = kmph
        }
        .build()

    private companion object {
        const val SETTLE_FOR_NO_WRITE = 300L
        const val RECORDER_SETTLE = 150L
    }
}
