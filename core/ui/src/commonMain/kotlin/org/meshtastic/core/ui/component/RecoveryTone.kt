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
package org.meshtastic.core.ui.component

/**
 * How loudly a [RecoveryCard] should present itself.
 *
 * Something being *absent* is not the same as something being *wrong*, and the permissions guidance is explicit that a
 * user's decision must be respected rather than pressured. A red error wash for a permission the app has not even asked
 * about yet reads as an accusation on a screen the user has done nothing wrong on.
 */
enum class RecoveryTone {
    /** Something is broken or blocked and the user is likely stuck. Error colours. */
    ERROR,

    /** Something is merely unavailable, and the card is offering to fix it. Neutral surface colours. */
    INFORMATIONAL,
}
