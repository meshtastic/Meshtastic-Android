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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.app_version
import org.meshtastic.core.resources.chirpy_hop
import org.meshtastic.core.resources.modules_already_unlocked
import org.meshtastic.core.resources.modules_unlocked
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.icon.Memory
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PlayArrow
import org.meshtastic.core.ui.util.rememberShowToastResource
import kotlin.time.Duration.Companion.seconds

private const val UNLOCK_CLICK_COUNT = 5 // Number of clicks required to unlock excluded modules.
private const val UNLOCKED_CLICK_COUNT = 3 // Number of clicks before we toast that modules are already unlocked.
private const val UNLOCK_TIMEOUT_SECONDS = 1 // Timeout in seconds to reset the click counter.

/** The app version row, which unlocks hidden features after [UNLOCK_CLICK_COUNT] quick taps. */
@Composable
internal fun AppVersionButton(
    hiddenFeaturesUnlocked: Boolean,
    appVersionName: String,
    onUnlockHiddenFeatures: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val showToast = rememberShowToastResource()
    var clickCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(clickCount) {
        if (clickCount in 1..<UNLOCK_CLICK_COUNT) {
            delay(UNLOCK_TIMEOUT_SECONDS.seconds)
            clickCount = 0
        }
    }

    ListItem(
        text = stringResource(Res.string.app_version),
        leadingIcon = MeshtasticIcons.Memory,
        supportingText = appVersionName,
        trailingIcon = null,
    ) {
        clickCount = clickCount.inc().coerceIn(0, UNLOCK_CLICK_COUNT)

        when {
            clickCount == UNLOCKED_CLICK_COUNT && hiddenFeaturesUnlocked -> {
                clickCount = 0
                scope.launch { showToast(Res.string.modules_already_unlocked) }
            }

            clickCount == UNLOCK_CLICK_COUNT -> {
                clickCount = 0
                onUnlockHiddenFeatures()
                scope.launch { showToast(Res.string.modules_unlocked) }
            }
        }
    }
}

/** The version row, followed by Chirpy Hop once the version row's taps have unlocked hidden features. */
@Composable
internal fun AppVersionRows(
    hiddenFeaturesUnlocked: Boolean,
    appVersionName: String,
    onUnlockHiddenFeatures: () -> Unit,
    onPlayChirpyHop: () -> Unit,
) {
    AppVersionButton(
        hiddenFeaturesUnlocked = hiddenFeaturesUnlocked,
        appVersionName = appVersionName,
        onUnlockHiddenFeatures = onUnlockHiddenFeatures,
    )
    if (hiddenFeaturesUnlocked) {
        ListItem(
            text = stringResource(Res.string.chirpy_hop),
            leadingIcon = MeshtasticIcons.PlayArrow,
            onClick = onPlayChirpyHop,
        )
    }
}
