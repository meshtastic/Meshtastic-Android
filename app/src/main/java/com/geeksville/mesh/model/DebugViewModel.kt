package com.geeksville.mesh.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.MeshLogRepository
import com.geeksville.mesh.database.entity.MeshLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val meshLogRepository: MeshLogRepository,
) : ViewModel(), Logging {

    private val _meshLog = MutableStateFlow<List<MeshLog>>(emptyList())
    val meshLog: StateFlow<List<MeshLog>> = _meshLog

    init {
        viewModelScope.launch {
            meshLogRepository.getAllLogs().collect { _meshLog.value = it }
        }

        debug("DebugViewModel created")
    }

    override fun onCleared() {
        super.onCleared()
        debug("DebugViewModel cleared")
    }

    fun deleteAllLogs() = viewModelScope.launch(Dispatchers.IO) {
        meshLogRepository.deleteAll()
    }
}
