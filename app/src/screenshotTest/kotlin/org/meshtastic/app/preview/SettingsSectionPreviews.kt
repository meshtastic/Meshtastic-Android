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
package org.meshtastic.app.preview

import androidx.compose.runtime.Composable
import org.meshtastic.feature.settings.component.AppInfoSectionPreview
import org.meshtastic.feature.settings.component.AppearanceSectionPreview
import org.meshtastic.feature.settings.component.PersistenceSectionPreview

/** Re-exports of internal settings section previews for screenshot testing. */
@MultiPreview
@Composable
fun SettingsAppInfoSectionPreview() {
    AppInfoSectionPreview()
}

@MultiPreview
@Composable
fun SettingsAppearanceSectionPreview() {
    AppearanceSectionPreview()
}

@MultiPreview
@Composable
fun SettingsPersistenceSectionPreview() {
    PersistenceSectionPreview()
}
