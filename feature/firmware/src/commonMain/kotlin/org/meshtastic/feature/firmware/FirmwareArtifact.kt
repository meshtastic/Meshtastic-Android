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
package org.meshtastic.feature.firmware

import org.meshtastic.core.common.util.CommonUri

/**
 * Platform-neutral handle for a firmware file or extracted artifact.
 *
 * @property uri Location of the artifact, typically a `file://` temp file or a user-provided content/file URI.
 * @property fileName Optional display name used for save/export prompts.
 * @property isTemporary Whether the current host owns the artifact and may safely delete it during cleanup.
 */
data class FirmwareArtifact(val uri: CommonUri, val fileName: String? = null, val isTemporary: Boolean = false)
