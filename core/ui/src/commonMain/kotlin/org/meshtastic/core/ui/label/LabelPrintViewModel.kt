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

    /** Bluetooth is off, unsupported or not permitted, so no printer can be found. */
    data object Bluetooth : LabelPrintError
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

    init {
        // A printer that drops out (auto power-off, out of range) is looked for again and reconnects when seen.
        printer.connectionState
            .onEach { connection ->
                val inUse = _state.value.status in setOf(LabelPrinterStatus.READY, LabelPrinterStatus.PRINTING)
                if (connection is BleConnectionState.Disconnected && inUse) startSearch()
            }
            .launchIn(viewModelScope)
    }

    fun setLabelLengthMm(millimeters: Int) = prefs.setLabelLengthMm(millimeters)

    /** Starts looking for printers, unless one is already being looked for or in use. */
    fun start() {
        if (_state.value.status == LabelPrinterStatus.IDLE) startSearch()
    }

    /** Ends the search and the connection; called when the sheet closes. */
    fun stop() {
        search?.cancel()
        _state.value = LabelPrintState()
        viewModelScope.launch { printer.disconnect() }
    }

    fun connect(device: BleDevice) {
        if (_state.value.status != LabelPrinterStatus.SEARCHING && _state.value.status != LabelPrinterStatus.IDLE) {
            return
        }
        search?.cancel()
        _state.update { it.copy(status = LabelPrinterStatus.CONNECTING, printerName = device.name, error = null) }
        viewModelScope.launch {
            safeCatching { printer.connect(device) }
                .onSuccess { _ ->
                    prefs.setPrinterAddress(device.address)
                    _state.update { it.copy(status = LabelPrinterStatus.READY) }
                }
                .onFailure { failure ->
                    _state.update { it.copy(status = LabelPrinterStatus.IDLE, error = failure.toLabelPrintError()) }
                }
        }
    }

    fun print(bitmap: LabelBitmap) {
        if (_state.value.status != LabelPrinterStatus.READY) return
        _state.update { it.copy(status = LabelPrinterStatus.PRINTING, error = null) }
        viewModelScope.launch {
            val result = safeCatching { printer.print(bitmap) }
            _state.update { state ->
                // A print that failed because the printer went away has already left PRINTING for a new search.
                val status = if (state.status == LabelPrinterStatus.PRINTING) LabelPrinterStatus.READY else state.status
                state.copy(
                    status = status,
                    printed = state.printed || result.isSuccess,
                    error = result.exceptionOrNull()?.toLabelPrintError(),
                )
            }
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
                    if (device.address == prefs.printerAddress.value) connect(device)
                }
            }
            _state.update { state ->
                if (state.status == LabelPrinterStatus.SEARCHING) {
                    state.copy(
                        status = LabelPrinterStatus.IDLE,
                        error = if (scan.isFailure) LabelPrintError.Bluetooth else state.error,
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
