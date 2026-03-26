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

/**
 * A container for all mesh-related fakes to simplify test setup.
 *
 * Instead of manually instantiating and wiring multiple fakes, you can use [FakeMeshService] to get a consistent set of
 * test doubles.
 */
class FakeMeshService {
    val nodeRepository = FakeNodeRepository()
    val serviceRepository = FakeServiceRepository()
    val radioController = FakeRadioController()
    val radioInterfaceService = FakeRadioInterfaceService()
    val notifications = FakeMeshServiceNotifications()
    val transport = FakeRadioTransport()
    val logRepository = FakeMeshLogRepository()
    val packetRepository = FakePacketRepository()
    val contactRepository = FakeContactRepository()
    val locationRepository = FakeLocationRepository()

    // Add more as they are implemented
}
