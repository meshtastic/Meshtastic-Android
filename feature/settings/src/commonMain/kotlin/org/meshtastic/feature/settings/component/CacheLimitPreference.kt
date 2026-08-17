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
package org.meshtastic.feature.settings.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.DatabaseConstants
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.apply
import org.meshtastic.core.resources.are_you_sure
import org.meshtastic.core.resources.cache_limit_eviction_warning
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.device_db_cache_limit
import org.meshtastic.core.resources.device_db_cache_limit_summary
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.MeshtasticTextDialog

/**
 * Device DB cache limit dropdown, shared between the Android and Desktop settings screens. Lowering the limit below the
 * number of currently-cached device databases evicts the least-recently-used ones and permanently deletes their local
 * history, so a lower value is gated behind a confirmation showing how many devices would be affected. Raising or
 * leaving the limit unchanged never evicts anything and applies immediately.
 */
@Composable
fun CacheLimitPreference(cacheLimit: Int, onCheckEvictionCount: suspend (Int) -> Int, onSetCacheLimit: (Int) -> Unit) {
    val cacheItems = remember {
        (DatabaseConstants.MIN_CACHE_LIMIT..DatabaseConstants.MAX_CACHE_LIMIT).map { it.toLong() to it.toString() }
    }
    val scope = rememberCoroutineScope()
    var pendingLimit by rememberSaveable { mutableStateOf<Int?>(null) }
    var pendingEvictionCount by rememberSaveable { mutableIntStateOf(0) }
    var selectionRequest by remember { mutableIntStateOf(0) }

    DropDownPreference(
        title = stringResource(Res.string.device_db_cache_limit),
        enabled = true,
        items = cacheItems,
        selectedItem = cacheLimit.toLong(),
        onItemSelected = { selected ->
            val request = ++selectionRequest
            val newLimit = selected.toInt()
            if (newLimit >= cacheLimit) {
                onSetCacheLimit(newLimit)
            } else {
                scope.launch {
                    val evicted = onCheckEvictionCount(newLimit)
                    if (request != selectionRequest) return@launch
                    if (evicted > 0) {
                        pendingEvictionCount = evicted
                        pendingLimit = newLimit
                    } else {
                        onSetCacheLimit(newLimit)
                    }
                }
            }
        },
        summary = stringResource(Res.string.device_db_cache_limit_summary),
    )

    pendingLimit?.let { limit ->
        MeshtasticTextDialog(
            titleRes = Res.string.are_you_sure,
            message =
            pluralStringResource(
                Res.plurals.cache_limit_eviction_warning,
                pendingEvictionCount,
                pendingEvictionCount,
            ),
            confirmTextRes = Res.string.apply,
            dismissTextRes = Res.string.cancel,
            onConfirm = {
                onSetCacheLimit(limit)
                pendingLimit = null
            },
            onDismiss = { pendingLimit = null },
        )
    }
}
