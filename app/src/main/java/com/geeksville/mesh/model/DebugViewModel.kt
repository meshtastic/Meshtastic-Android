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
import com.geeksville.mesh.AdminProtos
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.PaxcountProtos
import com.geeksville.mesh.Portnums.PortNum
import com.geeksville.mesh.StoreAndForwardProtos
import com.geeksville.mesh.TelemetryProtos
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.MeshLogRepository
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.ui.debug.FilterMode
import com.google.protobuf.InvalidProtocolBufferException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.meshtastic.core.database.entity.MeshLog
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SearchMatch(val logIndex: Int, val start: Int, val end: Int, val field: String)

data class SearchState(
    val searchText: String = "",
    val currentMatchIndex: Int = -1,
    val allMatches: List<SearchMatch> = emptyList(),
    val hasMatches: Boolean = false,
)

// --- Search and Filter Managers ---
class LogSearchManager {
    data class SearchMatch(val logIndex: Int, val start: Int, val end: Int, val field: String)

    data class SearchState(
        val searchText: String = "",
        val currentMatchIndex: Int = -1,
        val allMatches: List<SearchMatch> = emptyList(),
        val hasMatches: Boolean = false,
    )

    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    private val _currentMatchIndex = MutableStateFlow(-1)
    val currentMatchIndex = _currentMatchIndex.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState())
    val searchState = _searchState.asStateFlow()

    fun setSearchText(text: String) {
        _searchText.value = text
        _currentMatchIndex.value = -1
    }

    fun goToNextMatch() {
        val matches = _searchState.value.allMatches
        if (matches.isNotEmpty()) {
            val nextIndex = if (_currentMatchIndex.value < matches.lastIndex) _currentMatchIndex.value + 1 else 0
            _currentMatchIndex.value = nextIndex
            _searchState.value = _searchState.value.copy(currentMatchIndex = nextIndex)
        }
    }

    fun goToPreviousMatch() {
        val matches = _searchState.value.allMatches
        if (matches.isNotEmpty()) {
            val prevIndex = if (_currentMatchIndex.value > 0) _currentMatchIndex.value - 1 else matches.lastIndex
            _currentMatchIndex.value = prevIndex
            _searchState.value = _searchState.value.copy(currentMatchIndex = prevIndex)
        }
    }

    fun clearSearch() {
        setSearchText("")
    }

    fun updateMatches(searchText: String, filteredLogs: List<DebugViewModel.UiMeshLog>) {
        val matches = findSearchMatches(searchText, filteredLogs)
        val hasMatches = matches.isNotEmpty()
        _searchState.value =
            _searchState.value.copy(
                searchText = searchText,
                allMatches = matches,
                hasMatches = hasMatches,
                currentMatchIndex = if (hasMatches) _currentMatchIndex.value.coerceIn(0, matches.lastIndex) else -1,
            )
    }

    fun findSearchMatches(searchText: String, filteredLogs: List<DebugViewModel.UiMeshLog>): List<SearchMatch> {
        if (searchText.isEmpty()) {
            return emptyList()
        }
        return filteredLogs
            .flatMapIndexed { logIndex, log ->
                searchText.split(" ").flatMap { term ->
                    val escapedTerm = Regex.escape(term)
                    val regex = escapedTerm.toRegex(RegexOption.IGNORE_CASE)
                    val messageMatches =
                        regex.findAll(log.logMessage).map { match ->
                            SearchMatch(logIndex, match.range.first, match.range.last, "message")
                        }
                    val typeMatches =
                        regex.findAll(log.messageType).map { match ->
                            SearchMatch(logIndex, match.range.first, match.range.last, "type")
                        }
                    val dateMatches =
                        regex.findAll(log.formattedReceivedDate).map { match ->
                            SearchMatch(logIndex, match.range.first, match.range.last, "date")
                        }
                    val decodedPayloadMatches =
                        log.decodedPayload?.let { decoded ->
                            regex.findAll(decoded).map { match ->
                                SearchMatch(logIndex, match.range.first, match.range.last, "decodedPayload")
                            }
                        } ?: emptySequence()
                    messageMatches + typeMatches + dateMatches + decodedPayloadMatches
                }
            }
            .sortedBy { it.start }
    }
}

class LogFilterManager {
    private val _filterTexts = MutableStateFlow<List<String>>(emptyList())
    val filterTexts = _filterTexts.asStateFlow()

    private val _filteredLogs = MutableStateFlow<List<DebugViewModel.UiMeshLog>>(emptyList())
    val filteredLogs = _filteredLogs.asStateFlow()

    fun setFilterTexts(filters: List<String>) {
        _filterTexts.value = filters
    }

    fun updateFilteredLogs(logs: List<DebugViewModel.UiMeshLog>) {
        _filteredLogs.value = logs
    }

    fun filterLogs(
        logs: List<DebugViewModel.UiMeshLog>,
        filterTexts: List<String>,
        filterMode: FilterMode,
    ): List<DebugViewModel.UiMeshLog> {
        if (filterTexts.isEmpty()) return logs
        return logs.filter { log ->
            when (filterMode) {
                FilterMode.OR ->
                    filterTexts.any { filterText ->
                        log.logMessage.contains(filterText, ignoreCase = true) ||
                            log.messageType.contains(filterText, ignoreCase = true) ||
                            log.formattedReceivedDate.contains(filterText, ignoreCase = true) ||
                            (log.decodedPayload?.contains(filterText, ignoreCase = true) == true)
                    }
                FilterMode.AND ->
                    filterTexts.all { filterText ->
                        log.logMessage.contains(filterText, ignoreCase = true) ||
                            log.messageType.contains(filterText, ignoreCase = true) ||
                            log.formattedReceivedDate.contains(filterText, ignoreCase = true) ||
                            (log.decodedPayload?.contains(filterText, ignoreCase = true) == true)
                    }
            }
        }
    }
}

private const val HEX_FORMAT = "%02x"

@Suppress("TooManyFunctions")
@HiltViewModel
class DebugViewModel
@Inject
constructor(
    private val meshLogRepository: MeshLogRepository,
    private val radioConfigRepository: RadioConfigRepository,
) : ViewModel(),
    Logging {

    val meshLog: StateFlow<ImmutableList<UiMeshLog>> =
        meshLogRepository
            .getAllLogs()
            .map(::toUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentListOf())

    // --- Managers ---
    val searchManager = LogSearchManager()
    val filterManager = LogFilterManager()

    val searchText
        get() = searchManager.searchText

    val currentMatchIndex
        get() = searchManager.currentMatchIndex

    val searchState
        get() = searchManager.searchState

    val filterTexts
        get() = filterManager.filterTexts

    val filteredLogs
        get() = filterManager.filteredLogs

    private val _selectedLogId = MutableStateFlow<String?>(null)
    val selectedLogId = _selectedLogId.asStateFlow()

    fun updateFilteredLogs(logs: List<UiMeshLog>) {
        filterManager.updateFilteredLogs(logs)
        searchManager.updateMatches(searchManager.searchText.value, logs)
    }

    init {
        debug("DebugViewModel created")
        viewModelScope.launch {
            combine(searchManager.searchText, filterManager.filteredLogs) { searchText, logs ->
                searchManager.findSearchMatches(searchText, logs)
            }
                .collect { matches ->
                    searchManager.updateMatches(searchManager.searchText.value, filterManager.filteredLogs.value)
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        debug("DebugViewModel cleared")
    }

    private fun toUiState(databaseLogs: List<MeshLog>) = databaseLogs
        .map { log ->
            UiMeshLog(
                uuid = log.uuid,
                messageType = log.message_type,
                formattedReceivedDate = TIME_FORMAT.format(log.received_date),
                logMessage = annotateMeshLogMessage(log),
                decodedPayload = decodePayloadFromMeshLog(log),
            )
        }
        .toImmutableList()

    /** Transform the input [MeshLog] by enhancing the raw message with annotations. */
    private fun annotateMeshLogMessage(meshLog: MeshLog): String = when (meshLog.message_type) {
        "Packet" -> meshLog.meshPacket?.let { packet -> annotatePacketLog(packet) } ?: meshLog.raw_message
        "NodeInfo" ->
            meshLog.nodeInfo?.let { nodeInfo -> annotateRawMessage(meshLog.raw_message, nodeInfo.num) }
                ?: meshLog.raw_message
        "MyNodeInfo" ->
            meshLog.myNodeInfo?.let { nodeInfo -> annotateRawMessage(meshLog.raw_message, nodeInfo.myNodeNum) }
                ?: meshLog.raw_message
        else -> meshLog.raw_message
    }

    private fun annotatePacketLog(packet: MeshProtos.MeshPacket): String {
        val builder = packet.toBuilder()
        val hasDecoded = builder.hasDecoded()
        val decoded = if (hasDecoded) builder.decoded else null
        if (hasDecoded) builder.clearDecoded()
        val baseText = builder.build().toString().trimEnd()
        val result =
            if (hasDecoded && decoded != null) {
                val decodedText = decoded.toString().trimEnd().prependIndent("  ")
                "$baseText\ndecoded {\n$decodedText\n}"
            } else {
                baseText
            }
        return annotateRawMessage(result, packet.from, packet.to)
    }

    /** Annotate the raw message string with the node IDs provided, in hex, if they are present. */
    private fun annotateRawMessage(rawMessage: String, vararg nodeIds: Int): String {
        val msg = StringBuilder(rawMessage)
        var mutated = false
        nodeIds.toSet().forEach { nodeId -> mutated = mutated or msg.annotateNodeId(nodeId) }
        return if (mutated) {
            return msg.toString()
        } else {
            rawMessage
        }
    }

    /** Look for a single node ID integer in the string and annotate it with the hex equivalent if found. */
    private fun StringBuilder.annotateNodeId(nodeId: Int): Boolean {
        val nodeIdStr = nodeId.toUInt().toString()
        // Only match if whitespace before and after
        val regex = Regex("""(?<=\s|^)${Regex.escape(nodeIdStr)}(?=\s|$)""")
        regex.find(this)?.let { matchResult ->
            matchResult.groupValues.let { _ ->
                regex.findAll(this).toList().asReversed().forEach { match ->
                    val idx = match.range.last + 1
                    insert(idx, " (${nodeId.asNodeId()})")
                }
            }
            return true
        }
        return false
    }

    private fun Int.asNodeId(): String = "!%08x".format(Locale.getDefault(), this)

    fun deleteAllLogs() = viewModelScope.launch(Dispatchers.IO) { meshLogRepository.deleteAll() }

    @Immutable
    data class UiMeshLog(
        val uuid: String,
        val messageType: String,
        val formattedReceivedDate: String,
        val logMessage: String,
        val decodedPayload: String? = null,
    )

    companion object {
        private val TIME_FORMAT = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    }

    val presetFilters: List<String>
        get() = buildList {
            // Our address if available
            radioConfigRepository.myNodeInfo.value?.myNodeNum?.let { add("!%08x".format(it)) }
            // broadcast
            add("!ffffffff")
            // decoded
            add("decoded")
            // today (locale-dependent short date format)
            add(DateFormat.getDateInstance(DateFormat.SHORT).format(Date()))
            // Each app name
            addAll(PortNum.entries.map { it.name })
        }

    fun setSelectedLogId(id: String?) {
        _selectedLogId.value = id
    }

    /**
     * Attempts to fully decode the payload of a MeshLog's MeshPacket using the appropriate protobuf definition, based
     * on the portnum of the packet.
     *
     * For known portnums, the payload is parsed into its corresponding proto message and returned as a string. For text
     * and alert messages, the payload is interpreted as UTF-8 text. For unknown portnums, the payload is shown as a hex
     * string.
     *
     * @param log The MeshLog containing the packet and payload to decode.
     * @return A human-readable string representation of the decoded payload, or an error message if decoding fails, or
     *   null if the log does not contain a decodable packet.
     */
    private fun decodePayloadFromMeshLog(log: MeshLog): String? {
        var result: String? = null
        val packet = log.meshPacket
        if (packet == null || !packet.hasDecoded()) {
            result = null
        } else {
            val portnum = packet.decoded.portnumValue
            val payload = packet.decoded.payload.toByteArray()
            result =
                try {
                    when (portnum) {
                        PortNum.TEXT_MESSAGE_APP_VALUE,
                        PortNum.ALERT_APP_VALUE,
                        -> payload.toString(Charsets.UTF_8)
                        PortNum.POSITION_APP_VALUE -> MeshProtos.Position.parseFrom(payload).toString()
                        PortNum.WAYPOINT_APP_VALUE -> MeshProtos.Waypoint.parseFrom(payload).toString()
                        PortNum.NODEINFO_APP_VALUE -> MeshProtos.User.parseFrom(payload).toString()
                        PortNum.TELEMETRY_APP_VALUE -> TelemetryProtos.Telemetry.parseFrom(payload).toString()
                        PortNum.ROUTING_APP_VALUE -> MeshProtos.Routing.parseFrom(payload).toString()
                        PortNum.ADMIN_APP_VALUE -> AdminProtos.AdminMessage.parseFrom(payload).toString()
                        PortNum.PAXCOUNTER_APP_VALUE -> PaxcountProtos.Paxcount.parseFrom(payload).toString()
                        PortNum.STORE_FORWARD_APP_VALUE ->
                            StoreAndForwardProtos.StoreAndForward.parseFrom(payload).toString()
                        else -> payload.joinToString(" ") { HEX_FORMAT.format(it) }
                    }
                } catch (e: InvalidProtocolBufferException) {
                    "Failed to decode payload: ${e.message}"
                }
        }
        return result
    }
}
