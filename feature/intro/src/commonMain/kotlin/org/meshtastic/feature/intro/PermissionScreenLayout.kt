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
package org.meshtastic.feature.intro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.next
import org.meshtastic.core.resources.open_settings
import org.meshtastic.core.resources.skip
import org.meshtastic.core.ui.component.OnboardingBottomBar
import org.meshtastic.core.ui.component.OnboardingFeature
import org.meshtastic.core.ui.component.OnboardingFeatureRow
import org.meshtastic.core.ui.component.OnboardingNotice
import org.meshtastic.core.ui.component.OnboardingPageHeader
import org.meshtastic.core.ui.icon.AppSettingsAlt
import org.meshtastic.core.ui.icon.Info
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.PermissionStatus

/**
 * A generic layout for the permission screens of the app introduction flow: a headline, a description, a list of
 * features, and a bottom bar whose primary action follows the live [PermissionStatus].
 *
 * The primary action is status-driven rather than granted/not-granted, because the three not-granted states need three
 * different actions:
 * - [PermissionStatus.NOT_REQUESTED] / [PermissionStatus.DENIED_CAN_RETRY] — request; the system will show its dialog.
 * - [PermissionStatus.PERMANENTLY_DENIED] — the system will not show a dialog again, so requesting is a no-op that
 *   leaves the button looking broken. Route to app settings instead.
 * - [PermissionStatus.GRANTED] — nothing left to configure; advance.
 *
 * Both denied states also render an inline notice naming what stays disabled, which is the educational UI the Android
 * permissions guidance asks for before a re-request. The notice is a polite live region so a screen reader announces
 * the change when the user returns from a dialog they dismissed.
 *
 * @param headlineRes String resource for the main headline of the screen.
 * @param descriptionRes String resource for the main descriptive text.
 * @param features A list of [OnboardingFeature] to be displayed using [OnboardingFeatureRow].
 * @param status The live permission status driving the primary action and the notice.
 * @param deniedNoticeRes Notice shown after a single denial: what stays disabled if the user declines again.
 * @param blockedNoticeRes Notice shown once the system stops prompting: what stays disabled and where to turn it on.
 * @param configureButtonTextRes Primary button label while the permission can still be requested.
 * @param onSkip Callback for the skip action.
 * @param onPrimaryAction Callback for the primary action; the caller maps [status] to request / settings / advance.
 * @param additionalContent Optional composable lambda for adding custom content below the features.
 */
@Composable
internal fun PermissionScreenLayout(
    headlineRes: StringResource,
    descriptionRes: StringResource,
    features: List<OnboardingFeature>,
    status: PermissionStatus,
    deniedNoticeRes: StringResource,
    blockedNoticeRes: StringResource,
    configureButtonTextRes: StringResource,
    onSkip: () -> Unit,
    onPrimaryAction: () -> Unit,
    additionalContent: (@Composable () -> Unit)? = null,
) {
    val primaryLabelRes =
        when (status) {
            PermissionStatus.GRANTED -> Res.string.next

            PermissionStatus.PERMANENTLY_DENIED -> Res.string.open_settings

            PermissionStatus.NOT_REQUESTED,
            PermissionStatus.DENIED_CAN_RETRY,
            -> configureButtonTextRes
        }

    Scaffold(
        bottomBar = {
            OnboardingBottomBar(
                primaryText = stringResource(primaryLabelRes),
                onPrimary = onPrimaryAction,
                secondaryText = stringResource(Res.string.skip),
                onSecondary = onSkip,
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
            Modifier.fillMaxSize().padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OnboardingPageHeader(headline = stringResource(headlineRes), body = stringResource(descriptionRes))
            Spacer(modifier = Modifier.height(16.dp))
            // Directly under the description, above the feature list. Placed last it sat below up to four feature
            // rows in a scrolling column — off-screen on a typical phone, so a user who had just declined saw the
            // button label change and no reason why. It renders only after a denial, so the happy path pays nothing.
            PermissionNotice(status = status, deniedNoticeRes = deniedNoticeRes, blockedNoticeRes = blockedNoticeRes)
            features.forEach { feature ->
                OnboardingFeatureRow(feature = feature)
                Spacer(modifier = Modifier.height(16.dp))
            }
            additionalContent?.invoke()
        }
    }
}

/**
 * Inline explanation of what the user loses by declining, shown only once they actually have declined.
 *
 * Deliberately quieter than [org.meshtastic.core.ui.component.RecoveryCard]: onboarding is not an error state, and an
 * `errorContainer` wash on a screen the user is allowed to skip would read as a scolding.
 */
@Composable
private fun PermissionNotice(
    status: PermissionStatus,
    deniedNoticeRes: StringResource,
    blockedNoticeRes: StringResource,
) {
    val noticeRes =
        when (status) {
            PermissionStatus.DENIED_CAN_RETRY -> deniedNoticeRes

            PermissionStatus.PERMANENTLY_DENIED -> blockedNoticeRes

            PermissionStatus.NOT_REQUESTED,
            PermissionStatus.GRANTED,
            -> return
        }
    val icon =
        if (status == PermissionStatus.PERMANENTLY_DENIED) MeshtasticIcons.AppSettingsAlt else MeshtasticIcons.Info

    // Column rather than a bare Surface plus Spacer: the notice now sits mid-list, so it owns the gap below it, and a
    // composable may only emit from one source at its top level.
    Column(modifier = Modifier.fillMaxWidth()) {
        OnboardingNotice(text = stringResource(noticeRes), icon = icon)
        Spacer(modifier = Modifier.height(16.dp))
    }
}
