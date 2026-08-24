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
package org.meshtastic.core.testing

import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Position
import org.meshtastic.core.repository.AwaitedSendResult
import org.meshtastic.core.repository.AwaitedSendStatus
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig

/** Shared recording [CommandSender] fake for tests that do not need transport behavior. */
@Suppress("TooManyFunctions")
class FakeCommandSender :
    BaseFake(),
    CommandSender {

    data class AdminRequest(
        val destNum: Int,
        val requestId: Int,
        val wantResponse: Boolean,
        val message: AdminMessage,
        val expectedConnectionVersion: Long? = null,
    )

    data class TelemetryRequest(
        val requestId: Int,
        val destNum: Int,
        val typeValue: Int,
        val expectedConnectionVersion: Long? = null,
    )

    private val mutableSentPackets = mutableListOf<DataPacket>()
    val sentPackets: List<DataPacket>
        get() = mutableSentPackets.map { it.snapshot() }

    private val mutableAdminRequests = mutableListOf<AdminRequest>()
    val adminRequests: List<AdminRequest>
        get() = mutableAdminRequests.toList()

    private val mutableTelemetryRequests = mutableListOf<TelemetryRequest>()
    val telemetryRequests: List<TelemetryRequest>
        get() = mutableTelemetryRequests.toList()

    private var nextPacketId = 1

    var lastPassphrase: String? = null
        private set

    var lastBoots: Int = 0
        private set

    var lastHours: Int = 0
        private set

    var lastMaxSessionSeconds: Int = 0
        private set

    var lastDisable: Boolean = false
        private set

    var lockNowCalled: Boolean = false
        private set

    var awaitedAdminResult: AwaitedSendResult =
        AwaitedSendResult(AwaitedSendStatus.ACCEPTED, departureEpochAtDispatch = 0)
    var sendDataFailure: PacketQueueRejectedException? = null
    var commandFailure: Exception? = null

    init {
        registerResetAction {
            mutableSentPackets.clear()
            mutableAdminRequests.clear()
            mutableTelemetryRequests.clear()
            nextPacketId = 1
            lastPassphrase = null
            lastBoots = 0
            lastHours = 0
            lastMaxSessionSeconds = 0
            lastDisable = false
            lockNowCalled = false
            awaitedAdminResult = AwaitedSendResult(AwaitedSendStatus.ACCEPTED, departureEpochAtDispatch = 0)
            sendDataFailure = null
            commandFailure = null
        }
    }

    override fun getCurrentPacketId(): Long = nextPacketId.toLong()

    override fun getCachedLocalConfig(): LocalConfig = LocalConfig()

    override fun getCachedChannelSet(): ChannelSet = ChannelSet()

    override fun generatePacketId(): Int = nextPacketId++

    override suspend fun sendData(p: DataPacket) {
        failCommandIfConfigured()
        require(p.dataType != 0) { "Port numbers must be non-zero!" }
        if (p.id == 0) p.id = generatePacketId()
        sendDataFailure?.let { failure ->
            p.status = MessageStatus.ERROR
            throw failure
        }
        p.status = MessageStatus.QUEUED
        p.time = nowMillis
        mutableSentPackets += p.snapshot()
    }

    private fun DataPacket.snapshot(): DataPacket = copy().also { it.errorMessage = errorMessage }

    override suspend fun sendAdmin(destNum: Int, requestId: Int, wantResponse: Boolean, initFn: () -> AdminMessage) {
        failCommandIfConfigured()
        mutableAdminRequests += AdminRequest(destNum, requestId, wantResponse, initFn())
    }

    override suspend fun sendAdminForConnection(
        destNum: Int,
        expectedConnectionVersion: Long,
        requestId: Int,
        wantResponse: Boolean,
        initFn: () -> AdminMessage,
    ) {
        failCommandIfConfigured()
        mutableAdminRequests += AdminRequest(destNum, requestId, wantResponse, initFn(), expectedConnectionVersion)
    }

    override fun sendAdminImmediate(destNum: Int, initFn: () -> AdminMessage) {
        failCommandIfConfigured()
        mutableAdminRequests += AdminRequest(destNum, requestId = 0, wantResponse = false, message = initFn())
    }

    override suspend fun sendAdminAwaitResult(
        destNum: Int,
        requestId: Int,
        wantResponse: Boolean,
        initFn: () -> AdminMessage,
    ): AwaitedSendResult {
        failCommandIfConfigured()
        mutableAdminRequests += AdminRequest(destNum, requestId, wantResponse, initFn())
        return awaitedAdminResult
    }

    override suspend fun sendPosition(pos: org.meshtastic.proto.Position, destNum: Int?, wantResponse: Boolean) {
        failCommandIfConfigured()
    }

    override suspend fun requestPosition(destNum: Int, currentPosition: Position) {
        failCommandIfConfigured()
    }

    override suspend fun setFixedPosition(destNum: Int, pos: Position) {
        failCommandIfConfigured()
    }

    override suspend fun requestUserInfo(destNum: Int) {
        failCommandIfConfigured()
    }

    override suspend fun requestTraceroute(requestId: Int, destNum: Int) {
        failCommandIfConfigured()
    }

    override suspend fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) {
        failCommandIfConfigured()
        mutableTelemetryRequests += TelemetryRequest(requestId, destNum, typeValue)
    }

    override suspend fun requestTelemetryForConnection(
        requestId: Int,
        destNum: Int,
        typeValue: Int,
        expectedConnectionVersion: Long,
    ) {
        failCommandIfConfigured()
        mutableTelemetryRequests += TelemetryRequest(requestId, destNum, typeValue, expectedConnectionVersion)
    }

    override suspend fun requestNeighborInfo(requestId: Int, destNum: Int) {
        failCommandIfConfigured()
    }

    private fun failCommandIfConfigured() {
        commandFailure?.let { throw it }
    }

    override fun sendLockdownPassphrase(
        passphrase: String,
        boots: Int,
        hours: Int,
        maxSessionSeconds: Int,
        disable: Boolean,
    ) {
        failCommandIfConfigured()
        lastPassphrase = passphrase
        lastBoots = boots
        lastHours = hours
        lastMaxSessionSeconds = maxSessionSeconds
        lastDisable = disable
    }

    override fun sendLockNow() {
        failCommandIfConfigured()
        lockNowCalled = true
    }
}
