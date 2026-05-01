/*
 * Copyright (c) 2026 Meshtastic LLC
 */
package org.meshtastic.core.prefs.email

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.EmailPrefs

@Single
class EmailPrefsImpl(
    @Named("EmailDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : EmailPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val emailEnabled: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_EMAIL_ENABLED] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setEmailEnabled(enabled: Boolean) {
        scope.launch { dataStore.edit { it[KEY_EMAIL_ENABLED] = enabled } }
    }

    companion object {
        val KEY_EMAIL_ENABLED = booleanPreferencesKey("email_enabled")
    }
}
