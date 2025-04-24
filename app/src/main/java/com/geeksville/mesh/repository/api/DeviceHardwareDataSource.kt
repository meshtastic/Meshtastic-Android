package com.geeksville.mesh.repository.api

import com.geeksville.mesh.api.ApiService
import com.geeksville.mesh.database.entity.DeviceHardwareEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DeviceHardwareApiDataSource @Inject constructor(
    private val apiService: ApiService,
) {
    suspend fun getAllDeviceHardware(): List<DeviceHardwareEntity> = withContext(Dispatchers.IO) {
        apiService.getDeviceHardware().body() ?: emptyList()
    }
}

interface DeviceHardwareApi {
    suspend fun getAllDeviceHardware(): List<DeviceHardwareEntity>
}
