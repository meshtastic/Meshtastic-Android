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

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.StringResource
import javax.inject.Inject
import javax.inject.Singleton

fun interface ComposableContent {
    @Composable fun Content()
}

/**
 * A global manager for displaying alerts across the application. This allows ViewModels to trigger alerts without
 * direct dependencies on UI components.
 */
@Singleton
class AlertManager @Inject constructor() {
    data class AlertData(
        val title: String? = null,
        val titleRes: StringResource? = null,
        val message: String? = null,
        val messageRes: StringResource? = null,
        val composableMessage: ComposableContent? = null,
        val html: String? = null,
        val icon: ImageVector? = null,
        val onConfirm: (() -> Unit)? = null,
        val onDismiss: (() -> Unit)? = null,
        val confirmText: String? = null,
        val confirmTextRes: StringResource? = null,
        val dismissText: String? = null,
        val dismissTextRes: StringResource? = null,
        val choices: Map<String, () -> Unit> = emptyMap(),
        val dismissable: Boolean = true,
    )

    private val _currentAlert = MutableStateFlow<AlertData?>(null)
    val currentAlert = _currentAlert.asStateFlow()

    fun showAlert(
        title: String? = null,
        titleRes: StringResource? = null,
        message: String? = null,
        messageRes: StringResource? = null,
        composableMessage: ComposableContent? = null,
        html: String? = null,
        icon: ImageVector? = null,
        onConfirm: (() -> Unit)? = {},
        onDismiss: (() -> Unit)? = null,
        confirmText: String? = null,
        confirmTextRes: StringResource? = null,
        dismissText: String? = null,
        dismissTextRes: StringResource? = null,
        choices: Map<String, () -> Unit> = emptyMap(),
        dismissable: Boolean = true,
    ) {
        _currentAlert.value =
            AlertData(
                title = title,
                titleRes = titleRes,
                message = message,
                messageRes = messageRes,
                composableMessage = composableMessage,
                html = html,
                icon = icon,
                onConfirm = {
                    onConfirm?.invoke()
                    dismissAlert()
                },
                onDismiss = {
                    onDismiss?.invoke()
                    dismissAlert()
                },
                confirmText = confirmText,
                confirmTextRes = confirmTextRes,
                dismissText = dismissText,
                dismissTextRes = dismissTextRes,
                choices = choices,
                dismissable = dismissable,
            )
    }

    fun dismissAlert() {
        _currentAlert.value = null
    }
}
