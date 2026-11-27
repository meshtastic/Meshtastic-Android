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
package org.meshtastic.core.ui.label

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanStartException
import org.meshtastic.core.ble.BleScanStartFailureReason
import org.meshtastic.core.ble.niimbot.LabelBitmap
import org.meshtastic.core.ble.niimbot.NiimbotException
import org.meshtastic.core.ble.niimbot.NiimbotFault
import org.meshtastic.core.ble.niimbot.NiimbotPrinterFactory
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.repository.LabelPrintPrefs

/** Why the sheet could not find, reach or finish a print on the printer. */
sealed interface LabelPrintError {
    data class Fault(val fault: NiimbotFault, val code: Int) : LabelPrintError

    data object Unsupported : LabelPrintError

    data object NoResponse : LabelPrintError

    /** The search for printers could not start. [reason] is null when the platform gave none. */
    data class Scan(val reason: BleScanStartFailureReason?) : LabelPrintError
}

enum class LabelPrinterStatus {
    /** Not looking: the sheet is closed, or a search ended without a printer. */
    IDLE,
    SEARCHING,
    CONNECTING,
    READY,
    PRINTING,
}

data class LabelPrintState(
    val status: LabelPrinterStatus = LabelPrinterStatus.IDLE,
    /** Name of the printer being connected to or in use. */
    val printerName: String? = null,
    val found: List<BleDevice> = emptyList(),
    val error: LabelPrintError? = null,
    val printed: Boolean = false,
)

/**
 * Finds a Niimbot printer, reconnects to the one used last as soon as it is seen, and prints. The printer has its own
 * Bluetooth connection, so the radio's is not involved.
 */
@KoinViewModel
class LabelPrintViewModel(printerFactory: NiimbotPrinterFactory, private val prefs: LabelPrintPrefs) : ViewModel() {
    private val printer = printerFactory.create(viewModelScope)
    private val _state = MutableStateFlow(LabelPrintState())
    val state: StateFlow<LabelPrintState> = _state.asStateFlow()
    val labelLengthMm: StateFlow<Int> = prefs.labelLengthMm

    private var search: Job? = null

    /** The connect or print in flight. It is canceled before another starts, so only the latest writes state. */
    private var work: Job? = null

    /** The disconnect [stop] started, which the next connect waits out. */
    private var closing: Job? = null

    /**
     * True once a dropped printer was searched for without the user asking. A second drop then waits for the user:
     * every search spends one of the few scan starts Android allows the app, which the radio's reconnect needs too.
     */
    private var searchedAfterDrop = false

    init {
        printer.connectionState
            .onEach { connection ->
                val inUse = _state.value.status in setOf(LabelPrinterStatus.READY, LabelPrinterStatus.PRINTING)
                if (connection is BleConnectionState.Disconnected && inUse) onPrinterDropped()
            }
            .launchIn(viewModelScope)
    }

    fun setLabelLengthMm(millimeters: Int) = prefs.setLabelLengthMm(millimeters)

    /** Starts looking for printers, unless one is already being looked for or in use. */
    fun start() {
        if (_state.value.status == LabelPrinterStatus.IDLE) {
            searchedAfterDrop = false
            startSearch()
        }
    }

    /** Ends the search, any connect or print in flight, and the connection; called when the sheet closes. */
    fun stop() {
        search?.cancel()
        work?.cancel()
        _state.value = LabelPrintState()
        closing = viewModelScope.launch { printer.disconnect() }
    }

    /** Connects to the printer the user picked. */
    fun connect(device: BleDevice) {
        searchedAfterDrop = false
        _state.update { it.copy(error = null) }
        connectTo(device)
    }

    fun print(bitmap: LabelBitmap) {
        if (_state.value.status != LabelPrinterStatus.READY) return
        _state.update { it.copy(status = LabelPrinterStatus.PRINTING, error = null) }
        work = viewModelScope.launch {
            val result = safeCatching { printer.print(bitmap) }
            ensureActive()
            if (result.isSuccess) searchedAfterDrop = false
            _state.update { state ->
                state.copy(
                    status = LabelPrinterStatus.READY,
                    printed = state.printed || result.isSuccess,
                    error = result.exceptionOrNull()?.toLabelPrintError(),
                )
            }
        }
    }

    private fun connectTo(device: BleDevice) {
        if (_state.value.status != LabelPrinterStatus.SEARCHING && _state.value.status != LabelPrinterStatus.IDLE) {
            return
        }
        search?.cancel()
        work?.cancel()
        _state.update { it.copy(status = LabelPrinterStatus.CONNECTING, printerName = device.name) }
        work = viewModelScope.launch {
            closing?.join()
            val result = safeCatching { printer.connect(device) }
            ensureActive()
            result
                .onSuccess { _ ->
                    prefs.setPrinterAddress(device.address)
                    _state.update { it.copy(status = LabelPrinterStatus.READY) }
                }
                .onFailure { failure ->
                    _state.update { it.copy(status = LabelPrinterStatus.IDLE, error = failure.toLabelPrintError()) }
                }
        }
    }

    /** The printer went away (auto power-off, out of range): its print is over, and it is looked for once. */
    private fun onPrinterDropped() {
        val wasPrinting = _state.value.status == LabelPrinterStatus.PRINTING
        work?.cancel()
        if (wasPrinting) _state.update { it.copy(error = LabelPrintError.NoResponse) }
        if (searchedAfterDrop) {
            _state.update { it.copy(status = LabelPrinterStatus.IDLE, printerName = null) }
        } else {
            searchedAfterDrop = true
            startSearch()
        }
    }

    private fun startSearch() {
        search?.cancel()
        _state.update { it.copy(status = LabelPrinterStatus.SEARCHING, printerName = null, found = emptyList()) }
        search = viewModelScope.launch {
            val scan = safeCatching {
                printer.scan().collect { device ->
                    _state.update { state ->
                        if (state.found.any { it.address == device.address }) {
                            state
                        } else {
                            state.copy(found = state.found + device)
                        }
                    }
                    if (device.address == prefs.printerAddress.value) connectTo(device)
                }
            }
            _state.update { state ->
                if (state.status == LabelPrinterStatus.SEARCHING) {
                    state.copy(
                        status = LabelPrinterStatus.IDLE,
                        error = scan.exceptionOrNull()?.toScanError() ?: state.error,
                    )
                } else {
                    state
                }
            }
        }
    }
}

internal fun Throwable.toLabelPrintError(): LabelPrintError = when (this) {
    is NiimbotException.PrinterFault -> LabelPrintError.Fault(NiimbotFault.of(code), code)
    is NiimbotException.Unsupported -> LabelPrintError.Unsupported
    else -> LabelPrintError.NoResponse
}

private fun Throwable.toScanError(): LabelPrintError = LabelPrintError.Scan((this as? BleScanStartException)?.reason)
