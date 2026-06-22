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
package org.meshtastic.core.network.repository

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

/**
 * Android implementation of [SerialDevicePresence] backed by [UsbRepository.serialDevices].
 *
 * Derives a [Set] of device keys (the map's keys) from the underlying [UsbRepository] flow so consumers do not depend
 * on the Android-only [android.hardware.usb.UsbDevice] / [com.hoho.android.usbserial.driver.UsbSerialDriver] types.
 */
@Single
class AndroidSerialDevicePresence(
    usbRepository: UsbRepository,
    @Named("ProcessLifecycle") processLifecycle: Lifecycle,
) : SerialDevicePresence {
    override val deviceKeys =
        usbRepository.serialDevices
            .map { it.keys.toSet() }
            .stateIn(processLifecycle.coroutineScope, SharingStarted.Eagerly, emptySet())
}
