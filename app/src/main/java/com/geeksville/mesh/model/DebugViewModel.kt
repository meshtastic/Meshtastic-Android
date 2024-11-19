package com.geeksville.mesh.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.MeshLogRepository
import com.geeksville.mesh.database.entity.MeshLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val meshLogRepository: MeshLogRepository,
) : ViewModel(), Logging {
    val meshLog: StateFlow<List<MeshLog>> = meshLogRepository.getAllLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
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
