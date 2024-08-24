package com.geeksville.mesh.repository.datastore

import android.content.Context
import com.geeksville.mesh.model.NodeListConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Class responsible for application configuration data.
 */
class ApplicationConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val sharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

    fun getNodeListConfig(): NodeListConfig {
        val jsonString = sharedPreferences.getString("node_list_config", null)
        return if (jsonString != null) {
            Json.decodeFromString(jsonString)
        } else {
            NodeListConfig()
        }
    }

    fun saveNodeListConfig(config: NodeListConfig) {
        val jsonString = Json.encodeToString(config)
        sharedPreferences.edit().putString("node_list_config", jsonString).apply()
    }

}
