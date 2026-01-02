/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.service

import android.os.RemoteException

open class RadioNotConnectedException(message: String = "Not connected to radio") : RemoteException(message)

class NoDeviceConfigException(message: String = "No radio settings received (is our app too old?)") :
    RadioNotConnectedException(message)

class BLEException(message: String) : RadioNotConnectedException(message)

class BLECharacteristicNotFoundException(message: String) : RadioNotConnectedException(message)

class BLEConnectionClosing(message: String = "BLE connection is closing") : RadioNotConnectedException(message)
