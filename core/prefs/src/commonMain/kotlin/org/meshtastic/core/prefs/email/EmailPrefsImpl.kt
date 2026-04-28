/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.prefs.email

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

    override val smtpHost: StateFlow<String> =
        dataStore.data.map { it[KEY_SMTP_HOST] ?: "" }.stateIn(scope, SharingStarted.Eagerly, "")

    override fun setSmtpHost(host: String) {
        scope.launch { dataStore.edit { it[KEY_SMTP_HOST] = host } }
    }

    override val smtpPort: StateFlow<Int> =
        dataStore.data.map { it[KEY_SMTP_PORT] ?: 587 }.stateIn(scope, SharingStarted.Eagerly, 587)

    override fun setSmtpPort(port: Int) {
        scope.launch { dataStore.edit { it[KEY_SMTP_PORT] = port } }
    }

    override val smtpUser: StateFlow<String> =
        dataStore.data.map { it[KEY_SMTP_USER] ?: "" }.stateIn(scope, SharingStarted.Eagerly, "")

    override fun setSmtpUser(user: String) {
        scope.launch { dataStore.edit { it[KEY_SMTP_USER] = user } }
    }

    override val smtpPassword: StateFlow<String> =
        dataStore.data.map { it[KEY_SMTP_PASSWORD] ?: "" }.stateIn(scope, SharingStarted.Eagerly, "")

    override fun setSmtpPassword(password: String) {
        scope.launch { dataStore.edit { it[KEY_SMTP_PASSWORD] = password } }
    }

    override val smtpAuth: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_SMTP_AUTH] ?: true }.stateIn(scope, SharingStarted.Eagerly, true)

    override fun setSmtpAuth(auth: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SMTP_AUTH] = auth } }
    }

    override val smtpStartTls: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_SMTP_STARTTLS] ?: true }.stateIn(scope, SharingStarted.Eagerly, true)

    override fun setSmtpStartTls(startTls: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SMTP_STARTTLS] = startTls } }
    }

    companion object {
        val KEY_EMAIL_ENABLED = booleanPreferencesKey("email_enabled")
        val KEY_SMTP_HOST = stringPreferencesKey("smtp_host")
        val KEY_SMTP_PORT = intPreferencesKey("smtp_port")
        val KEY_SMTP_USER = stringPreferencesKey("smtp_user")
        val KEY_SMTP_PASSWORD = stringPreferencesKey("smtp_password")
        val KEY_SMTP_AUTH = booleanPreferencesKey("smtp_auth")
        val KEY_SMTP_STARTTLS = booleanPreferencesKey("smtp_starttls")
    }
}
