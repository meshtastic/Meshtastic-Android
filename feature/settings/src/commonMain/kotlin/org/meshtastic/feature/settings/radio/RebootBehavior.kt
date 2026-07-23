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
package org.meshtastic.feature.settings.radio

/**
 * How the firmware applies a saved config section (firmware `AdminModule::handleSetConfig` / `handleSetModuleConfig`):
 * most sections are applied with a node reboot a few seconds after the save is acked.
 *
 * This map is deliberately COARSE — the firmware's reboot decision is field-level for some sections and changes between
 * releases, so the app only distinguishes "will restart", "may restart", and "never restarts". Bias toward
 * [MAY_RESTART] when unsure; claim [ALWAYS] only for sections that have rebooted across firmware generations.
 */
enum class RebootBehavior {
    /** The firmware applies this section with a reboot; the save UI says "Save & restart". */
    ALWAYS,

    /** The firmware reboots only when specific fields change; the save UI keeps the softer "may reboot" copy. */
    MAY_RESTART,

    /** Applied live — no reboot, no warning. */
    NEVER,
}
