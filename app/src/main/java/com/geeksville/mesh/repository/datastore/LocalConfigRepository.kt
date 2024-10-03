package com.geeksville.mesh.repository.datastore

import androidx.datastore.core.DataStore
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.ConfigProtos.Config
import com.geeksville.mesh.LocalOnlyProtos.LocalConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException
import javax.inject.Inject

/**
 * Class that handles saving and retrieving [LocalConfig] data.
 */
class LocalConfigRepository @Inject constructor(
    private val localConfigStore: DataStore<LocalConfig>,
) : Logging {
    val localConfigFlow: Flow<LocalConfig> = localConfigStore.data
        .catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                errormsg("Error reading LocalConfig settings: ${exception.message}")
                emit(LocalConfig.getDefaultInstance())
            } else {
                throw exception
            }
        }

    suspend fun clearLocalConfig() {
        localConfigStore.updateData { preference ->
            preference.toBuilder().clear().build()
        }
    }

    /**
     * Updates [LocalConfig] from each [Config] oneOf.
     */
    suspend fun setLocalConfig(config: Config) = localConfigStore.updateData {
        val builder = it.toBuilder()
        config.allFields.forEach { (field, value) ->
            val localField = it.descriptorForType.findFieldByName(field.name)
            if (localField != null) {
                builder.setField(localField, value)
            } else {
                errormsg("Error writing LocalConfig settings: ${config.payloadVariantCase}")
            }
        }
        builder.build()
    }
}
