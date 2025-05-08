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

package com.geeksville.mesh.model

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.MeshLogRepository
import com.geeksville.mesh.database.entity.MeshLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val meshLogRepository: MeshLogRepository,
) : ViewModel(), Logging {

    val meshLog: StateFlow<ImmutableList<UiMeshLog>> = meshLogRepository.getAllLogs()
        .map(::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentListOf())

    init {
        debug("DebugViewModel created")
    }

    override fun onCleared() {
        super.onCleared()
        debug("DebugViewModel cleared")
    }

    private fun toUiState(databaseLogs: List<MeshLog>) = databaseLogs.map { log ->
        UiMeshLog(
            uuid = log.uuid,
            messageType = log.message_type,
            formattedReceivedDate = TIME_FORMAT.format(log.received_date),
            logMessage = annotateMeshLogMessage(log),
        )
    }.toImmutableList()

    /**
     * Transform the input [MeshLog] by enhancing the raw message with annotations.
     */
    private fun annotateMeshLogMessage(meshLog: MeshLog): String {
        val annotated = when (meshLog.message_type) {
            "Packet" -> meshLog.meshPacket?.let { packet ->
                annotateRawMessage(meshLog.raw_message, packet.from, packet.to)
            }

            "NodeInfo" -> meshLog.nodeInfo?.let { nodeInfo ->
                annotateRawMessage(meshLog.raw_message, nodeInfo.num)
            }

            "MyNodeInfo" -> meshLog.myNodeInfo?.let { nodeInfo ->
                annotateRawMessage(meshLog.raw_message, nodeInfo.myNodeNum)
            }

            else -> null
        }
        return annotated ?: meshLog.raw_message
    }

    /**
     * Annotate the raw message string with the node IDs provided, in hex, if they are present.
     */
    private fun annotateRawMessage(rawMessage: String, vararg nodeIds: Int): String {
        val msg = StringBuilder(rawMessage)
        var mutated = false
        nodeIds.forEach { nodeId ->
            mutated = mutated or msg.annotateNodeId(nodeId)
        }
        return if (mutated) {
            return msg.toString()
        } else {
            rawMessage
        }
    }

    /**
     * Look for a single node ID integer in the string and annotate it with the hex equivalent
     * if found.
     */
    private fun StringBuilder.annotateNodeId(nodeId: Int): Boolean {
        val nodeIdStr = nodeId.toUInt().toString()
        indexOf(nodeIdStr).takeIf { it >= 0 }?.let { idx ->
            insert(idx + nodeIdStr.length, " (${nodeId.asNodeId()})")
            return true
        }
        return false
    }

    private fun Int.asNodeId(): String {
        return "!%08x".format(Locale.getDefault(), this)
    }

    fun deleteAllLogs() = viewModelScope.launch(Dispatchers.IO) {
        meshLogRepository.deleteAll()
    }

    @Immutable
    data class UiMeshLog(
        val uuid: String,
        val messageType: String,
        val formattedReceivedDate: String,
        val logMessage: String,
    )

    companion object {
        private val TIME_FORMAT = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    }
}
