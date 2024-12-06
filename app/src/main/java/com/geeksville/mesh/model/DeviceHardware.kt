package com.geeksville.mesh.model

import android.content.Context
import com.geeksville.mesh.MeshProtos.HardwareModel
import com.geeksville.mesh.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
) {
    companion object {
        fun getDeviceHardwareFromHardwareModel(
            context: Context,
            hwModel: HardwareModel
        ): DeviceHardware? {
            val json =
                context.assets.open("device_hardware.json").bufferedReader().use { it.readText() }
            val deviceHardware = Json.decodeFromString<List<DeviceHardware>>(json)
            return deviceHardware.find { it.hwModel == hwModel.number }
        }
    }

    fun getDeviceVectorImage(context: Context): Int {
        val resourceId = context.resources.getIdentifier(
            "hw_${this.hwModelSlug.lowercase()}",
            "drawable",
            context.packageName
        )
        if (resourceId == 0) {
            return R.drawable.hw_unknown
        }
        return resourceId
    }
}
