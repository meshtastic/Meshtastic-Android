/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.ui.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A global manager for displaying alerts across the application. This allows ViewModels to trigger alerts without
 * direct dependencies on UI components.
 */
@Singleton
class AlertManager @Inject constructor() {
    data class AlertData(
        val title: String,
        val message: String? = null,
        val html: String? = null,
        val onConfirm: (() -> Unit)? = null,
        val onDismiss: (() -> Unit)? = null,
        val choices: Map<String, () -> Unit> = emptyMap(),
    )

    private val _currentAlert = MutableStateFlow<AlertData?>(null)
    val currentAlert = _currentAlert.asStateFlow()

    fun showAlert(
        title: String,
        message: String? = null,
        html: String? = null,
        onConfirm: (() -> Unit)? = {},
        dismissable: Boolean = true,
        choices: Map<String, () -> Unit> = emptyMap(),
    ) {
        _currentAlert.value =
            AlertData(
                title = title,
                message = message,
                html = html,
                onConfirm = {
                    onConfirm?.invoke()
                    dismissAlert()
                },
                onDismiss = { if (dismissable) dismissAlert() },
                choices = choices,
            )
    }

    fun dismissAlert() {
        _currentAlert.value = null
    }
}
