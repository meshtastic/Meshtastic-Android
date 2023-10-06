package com.geeksville.mesh.service

import com.geeksville.mesh.IMeshService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceRepository @Inject constructor() {
    var meshService: IMeshService? = null
        private set

    fun setMeshService(service: IMeshService?) {
        meshService = service
    }
}
