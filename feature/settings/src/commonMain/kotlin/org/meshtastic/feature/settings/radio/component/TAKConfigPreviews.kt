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
@file:Suppress("PreviewPublic")

package org.meshtastic.feature.settings.radio.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.takserver.TakTestResult
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.MemberRole
import org.meshtastic.proto.Team

@PreviewLightDark
@Composable
fun TakConfigCardPreview() {
    AppTheme {
        TakConfigCard(
            team = Team.Cyan,
            role = MemberRole.TeamLead,
            enabled = true,
            onTeamSelected = {},
            onRoleSelected = {},
        )
    }
}

@PreviewLightDark
@Composable
fun TakServerSectionDisabledPreview() {
    AppTheme { TakServerSection(isTakServerEnabled = false, onEnabledChange = {}, onExport = {}) }
}

@PreviewLightDark
@Composable
fun TakServerSectionEnabledPreview() {
    AppTheme { TakServerSection(isTakServerEnabled = true, onEnabledChange = {}, onExport = {}) }
}

@PreviewLightDark
@Composable
fun TakTestCardIdlePreview() {
    AppTheme {
        TakMeshTestCardContent(
            results = emptyList(),
            isRunning = false,
            currentFixture = null,
            fixtureCount = 40,
            onRunTests = {},
        )
    }
}

@PreviewLightDark
@Composable
fun TakTestCardRunningPreview() {
    AppTheme {
        TakMeshTestCardContent(
            results =
            listOf(
                TakTestResult("a-f-G-U-C.xml", xmlBytes = 412, compressedBytes = 87, passed = true),
                TakTestResult("a-h-G.xml", xmlBytes = 389, compressedBytes = 92, passed = true),
            ),
            isRunning = true,
            currentFixture = "b-m-p-s-m.xml",
            fixtureCount = 40,
            onRunTests = {},
        )
    }
}

@PreviewLightDark
@Composable
fun TakTestCardResultsPreview() {
    AppTheme {
        TakMeshTestCardContent(
            results =
            listOf(
                TakTestResult("a-f-G-U-C.xml", xmlBytes = 412, compressedBytes = 87, passed = true),
                TakTestResult("a-h-G.xml", xmlBytes = 389, compressedBytes = 92, passed = true),
                TakTestResult(
                    "u-d-f-m.xml",
                    xmlBytes = 1850,
                    compressedBytes = 310,
                    passed = false,
                    error = "Exceeds MTU",
                ),
                TakTestResult("b-m-p-s-m.xml", xmlBytes = 520, compressedBytes = 115, passed = true),
            ),
            isRunning = false,
            currentFixture = null,
            fixtureCount = 40,
            onRunTests = {},
        )
    }
}
