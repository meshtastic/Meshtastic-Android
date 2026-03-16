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
package org.meshtastic.core.common.util

/**
 * A multiplatform representation of a URI, primarily used to safely pass
 * Android Uri references through commonMain modules without coupling them
 * to the android.net.Uri class.
 */
data class MeshtasticUri(val uriString: String) {
    override fun toString(): String = uriString

    companion object {
        fun parse(uriString: String): MeshtasticUri = MeshtasticUri(uriString)
    }
}
