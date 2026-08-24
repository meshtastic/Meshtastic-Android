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
package org.meshtastic.feature.settings.appfunctions

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.repository.AppFunctionsPrefs

@KoinViewModel
class AppFunctionsSettingsViewModel(private val prefs: AppFunctionsPrefs) : ViewModel() {

    val masterEnabled: StateFlow<Boolean> = prefs.masterEnabled
    val sendMessageEnabled: StateFlow<Boolean> = prefs.sendMessageEnabled
    val getMeshStatusEnabled: StateFlow<Boolean> = prefs.getMeshStatusEnabled
    val getNodeListEnabled: StateFlow<Boolean> = prefs.getNodeListEnabled
    val getChannelInfoEnabled: StateFlow<Boolean> = prefs.getChannelInfoEnabled
    val getDeviceStatusEnabled: StateFlow<Boolean> = prefs.getDeviceStatusEnabled
    val getNodeDetailsEnabled: StateFlow<Boolean> = prefs.getNodeDetailsEnabled
    val getMeshMetricsEnabled: StateFlow<Boolean> = prefs.getMeshMetricsEnabled
    val getRecentMessagesEnabled: StateFlow<Boolean> = prefs.getRecentMessagesEnabled
    val getUnreadSummaryEnabled: StateFlow<Boolean> = prefs.getUnreadSummaryEnabled

    fun setMasterEnabled(enabled: Boolean) = prefs.setMasterEnabled(enabled)

    fun setSendMessageEnabled(enabled: Boolean) = prefs.setSendMessageEnabled(enabled)

    fun setGetMeshStatusEnabled(enabled: Boolean) = prefs.setGetMeshStatusEnabled(enabled)

    fun setGetNodeListEnabled(enabled: Boolean) = prefs.setGetNodeListEnabled(enabled)

    fun setGetChannelInfoEnabled(enabled: Boolean) = prefs.setGetChannelInfoEnabled(enabled)

    fun setGetDeviceStatusEnabled(enabled: Boolean) = prefs.setGetDeviceStatusEnabled(enabled)

    fun setGetNodeDetailsEnabled(enabled: Boolean) = prefs.setGetNodeDetailsEnabled(enabled)

    fun setGetMeshMetricsEnabled(enabled: Boolean) = prefs.setGetMeshMetricsEnabled(enabled)

    fun setGetRecentMessagesEnabled(enabled: Boolean) = prefs.setGetRecentMessagesEnabled(enabled)

    fun setGetUnreadSummaryEnabled(enabled: Boolean) = prefs.setGetUnreadSummaryEnabled(enabled)
}
