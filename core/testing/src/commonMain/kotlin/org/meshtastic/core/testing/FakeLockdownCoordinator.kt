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

import org.meshtastic.core.repository.LockdownCoordinator
import org.meshtastic.proto.LockdownStatus

class FakeLockdownCoordinator : LockdownCoordinator {
    var connectCalled = false
    var disconnectCalled = false
    var configCompleteCalled = false
    var lastStatus: LockdownStatus? = null
    var lastPassphrase: String? = null
    var lastBoots: Int? = null
    var lastHours: Int? = null
    var lockNowCalled = false

    override fun onConnect() {
        connectCalled = true
    }

    override fun onDisconnect() {
        disconnectCalled = true
    }

    override fun onConfigComplete() {
        configCompleteCalled = true
    }

    override fun handleLockdownStatus(status: LockdownStatus) {
        lastStatus = status
    }

    override fun submitPassphrase(passphrase: String, boots: Int, hours: Int) {
        lastPassphrase = passphrase
        lastBoots = boots
        lastHours = hours
    }

    override fun lockNow() {
        lockNowCalled = true
    }
}
