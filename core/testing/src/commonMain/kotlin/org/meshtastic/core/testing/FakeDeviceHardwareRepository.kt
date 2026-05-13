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
package org.meshtastic.core.testing

import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.repository.DeviceHardwareRepository

/**
 * A test double for [DeviceHardwareRepository] backed by an in-memory map keyed by `(hwModel, target)`.
 *
 * Call [setHardware] (or [setHardwareForModel]) to seed results, or [setResult] to control the exact [Result] returned
 * for a given lookup. By default, lookups return `Result.success(null)`.
 */
class FakeDeviceHardwareRepository :
    BaseFake(),
    DeviceHardwareRepository {

    private val hardware = mutableMapOf<Pair<Int, String?>, Result<DeviceHardware?>>()
    private val calls = mutableListOf<Triple<Int, String?, Boolean>>()

    init {
        registerResetAction {
            hardware.clear()
            calls.clear()
        }
    }

    /** Records every [getDeviceHardwareByModel] invocation for assertion. */
    val recordedCalls: List<Triple<Int, String?, Boolean>>
        get() = calls.toList()

    override suspend fun getDeviceHardwareByModel(
        hwModel: Int,
        target: String?,
        forceRefresh: Boolean,
    ): Result<DeviceHardware?> {
        calls.add(Triple(hwModel, target, forceRefresh))
        return hardware[hwModel to target] ?: hardware[hwModel to null] ?: Result.success(null)
    }

    /** Seeds a successful lookup for the given model/target pair. */
    fun setHardware(hwModel: Int, target: String? = null, device: DeviceHardware?) {
        hardware[hwModel to target] = Result.success(device)
    }

    /** Seeds a successful lookup for any target of the given model. */
    fun setHardwareForModel(hwModel: Int, device: DeviceHardware?) {
        hardware[hwModel to null] = Result.success(device)
    }

    /** Seeds an arbitrary [Result] for the given lookup (use to test failure paths). */
    fun setResult(hwModel: Int, target: String? = null, result: Result<DeviceHardware?>) {
        hardware[hwModel to target] = result
    }
}
