package com.geeksville.mesh.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.geeksville.mesh.repository.bluetooth.BluetoothRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Thin view model which adapts the view layer to the `BluetoothRepository`.
 */
@HiltViewModel
class BluetoothViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothRepository,
) : ViewModel() {
    fun refreshState() = bluetoothRepository.refreshState()

    val enabled = bluetoothRepository.enabled.asLiveData()
}