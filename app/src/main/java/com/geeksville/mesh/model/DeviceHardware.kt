package com.geeksville.mesh.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceHardware(
    val hwModel: Int,
    val hwModelSlug: String,
    val platformioTarget: String,
    val architecture: String,
    val activelySupported: Boolean,
    val supportLevel: Int? = null,
    val displayName: String,
    val tags: List<String>? = listOf(),
    val images: List<String>? = listOf(),
    val requiresDfu: Boolean? = null
)
