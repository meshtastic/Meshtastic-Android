package com.geeksville.mesh.model

import android.app.Application
import androidx.lifecycle.ViewModel
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.repository.datastore.ApplicationConfigRepository
import com.geeksville.mesh.service.ServiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ApplicationConfigState(
    val route: String = "",
    val nodeListConfig: NodeListConfig = NodeListConfig()
)

@HiltViewModel
class ApplicationConfigViewModel @Inject constructor(
    private val app: Application,
    private val applicationConfigRepository: ApplicationConfigRepository,
    private val serviceRepository: ServiceRepository,

    ) : ViewModel(), Logging {

    private val _applicationConfigState = MutableStateFlow(ApplicationConfigState())
    val applicationConfigState: StateFlow<ApplicationConfigState> = _applicationConfigState

    init {
        loadConfig()
    }

    private fun loadConfig() {
        val savedConfig = applicationConfigRepository.getNodeListConfig()
        _applicationConfigState.value = _applicationConfigState.value.copy(
            nodeListConfig = savedConfig
        )
    }

    fun saveNodeListConfig(config: NodeListConfig) {
        _applicationConfigState.update { it.copy(nodeListConfig = config) }
        applicationConfigRepository.saveNodeListConfig(config)
        serviceRepository.meshService?.updatePacketResetCounterInterval(config.interval)

    }

}
