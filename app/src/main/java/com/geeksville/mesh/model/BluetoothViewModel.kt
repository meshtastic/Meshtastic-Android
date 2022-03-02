package com.geeksville.mesh.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.geeksville.mesh.repository.bluetooth.BluetoothRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Thin view model which adapts the view layer to the `BluetoothRepository`.
 */
@HiltViewModel
class BluetoothViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
) : ViewModel() {
    /**
     * Called when permissions have been updated.  This causes an explicit refresh of the
     * bluetooth state.
     */
    fun permissionsUpdated() = bluetoothRepository.refreshState()

    val enabled = bluetoothRepository.state.map { it.enabled }.asLiveData()
}