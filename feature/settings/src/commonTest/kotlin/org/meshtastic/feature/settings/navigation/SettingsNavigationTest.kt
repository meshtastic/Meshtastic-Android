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
package org.meshtastic.feature.settings.navigation

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import org.meshtastic.core.navigation.ConnectionsRoute
import org.meshtastic.core.navigation.NodesRoute
import org.meshtastic.core.navigation.SettingsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SettingsNavigationTest {

    @Test
    fun `settings destination follows the latest settings root through submenus`() {
        val stack =
            listOf<NavKey>(
                SettingsRoute.Settings(destNum = 1234),
                SettingsRoute.DeviceConfiguration,
                SettingsRoute.LoRa,
            )

        assertEquals(1234, settingsDestination(stack))
    }

    @Test
    fun `settings sessions distinguish inactive local and remote states`() {
        assertEquals(SettingsRadioConfigSession.Inactive, settingsRadioConfigSession(listOf(NodesRoute.Nodes)))
        assertEquals(SettingsRadioConfigSession.Local, settingsRadioConfigSession(listOf(SettingsRoute.Settings())))
        assertEquals(
            SettingsRadioConfigSession.Remote(1234),
            settingsRadioConfigSession(listOf(SettingsRoute.Settings(destNum = 1234))),
        )
    }

    @Test
    fun `direct local config route outside settings tab keeps a local session active`() {
        val stack = listOf<NavKey>(ConnectionsRoute.Connections(), SettingsRoute.LoRa)

        assertEquals(SettingsRadioConfigSession.Local, settingsRadioConfigSession(stack))
    }

    @Test
    fun `non radio settings route outside settings tab stays inactive`() {
        val stack = listOf<NavKey>(NodesRoute.Nodes, SettingsRoute.FilterSettings)

        assertEquals(SettingsRadioConfigSession.Inactive, settingsRadioConfigSession(stack))
    }

    @Test
    fun `settings destination returns to local for a newer local root`() {
        val stack =
            listOf<NavKey>(
                SettingsRoute.Settings(destNum = 1234),
                SettingsRoute.DeviceConfiguration,
                SettingsRoute.Settings(),
                SettingsRoute.ModuleConfiguration,
            )

        assertNull(settingsDestination(stack))
        assertEquals(SettingsRadioConfigSession.Local, settingsRadioConfigSession(stack))
    }

    @Test
    fun `same settings session retains its view model store`() {
        val holder = SettingsRadioConfigSessionHolder()
        val session = SettingsRadioConfigSession.Remote(1234)
        holder.activate(session)
        holder.retain(session)
        val firstOwner = holder.ownerFor(session)
        val viewModel = TrackingViewModel()
        firstOwner.viewModelStore.put("radio", viewModel)

        holder.activate(session)
        val secondOwner = holder.ownerFor(session)

        assertSame(firstOwner, secondOwner)
        assertFalse(viewModel.wasCleared)
        holder.release(session)
    }

    @Test
    fun `direct local route transition does not clear its active store between entries`() {
        val holder = SettingsRadioConfigSessionHolder()
        val session = settingsRadioConfigSession(listOf(ConnectionsRoute.Connections(), SettingsRoute.LoRa))
        assertEquals(SettingsRadioConfigSession.Local, session)

        holder.activate(session)
        val activeSession = session as SettingsRadioConfigSession.Active
        holder.retain(activeSession)
        val firstOwner = holder.ownerFor(activeSession)
        val viewModel = TrackingViewModel()
        firstOwner.viewModelStore.put("radio", viewModel)

        // The outgoing entry can release before the incoming entry acquires its lease. Because the direct route is an
        // active local settings session, that zero-lease transition must not evict the store.
        holder.release(activeSession)
        val secondOwner = holder.ownerFor(activeSession)
        holder.retain(activeSession)

        assertSame(firstOwner, secondOwner)
        assertFalse(viewModel.wasCleared)
        holder.release(activeSession)
    }

    @Test
    fun `configuration recreation keeps the active settings store`() {
        val holder = SettingsRadioConfigSessionHolder()
        val session = SettingsRadioConfigSession.Local
        holder.activate(session)
        holder.retain(session)
        val firstOwner = holder.ownerFor(session)
        val viewModel = TrackingViewModel()
        firstOwner.viewModelStore.put("radio", viewModel)

        // The old composition releases its lease during recreation, but the retained holder still marks this
        // session active.
        holder.release(session)
        val secondOwner = holder.ownerFor(session)
        holder.retain(session)

        assertSame(firstOwner, secondOwner)
        assertFalse(viewModel.wasCleared)
        holder.release(session)
    }

    @Test
    fun `changing destination clears the previous store after its exit lease ends`() {
        val holder = SettingsRadioConfigSessionHolder()
        val firstSession = SettingsRadioConfigSession.Remote(1234)
        val secondSession = SettingsRadioConfigSession.Remote(5678)
        holder.activate(firstSession)
        holder.retain(firstSession)
        val firstOwner = holder.ownerFor(firstSession)
        val firstViewModel = TrackingViewModel()
        firstOwner.viewModelStore.put("radio", firstViewModel)

        holder.activate(secondSession)
        val secondOwner = holder.ownerFor(secondSession)

        assertFalse(firstViewModel.wasCleared)
        assertNotSame(firstOwner, secondOwner)

        holder.release(firstSession)

        assertTrue(firstViewModel.wasCleared)
    }

    @Test
    fun `leaving settings clears the active store after its exit lease ends`() {
        val holder = SettingsRadioConfigSessionHolder()
        val session = SettingsRadioConfigSession.Local
        holder.activate(session)
        holder.retain(session)
        val owner = holder.ownerFor(session)
        val viewModel = TrackingViewModel()
        owner.viewModelStore.put("radio", viewModel)

        holder.activate(SettingsRadioConfigSession.Inactive)

        assertFalse(viewModel.wasCleared)

        holder.release(session)

        assertTrue(viewModel.wasCleared)
    }

    @Test
    fun `explicit entry session wins over the previous active destination`() {
        val holder = SettingsRadioConfigSessionHolder()
        holder.activate(SettingsRadioConfigSession.Remote(1234))

        assertEquals(SettingsRadioConfigSession.Local, holder.resolveEntrySession(SettingsRadioConfigSession.Local))
        assertEquals(
            SettingsRadioConfigSession.Remote(5678),
            holder.resolveEntrySession(SettingsRadioConfigSession.Remote(5678)),
        )
    }

    @Test
    fun `inactive exit transition resolves the most recent active session`() {
        val holder = SettingsRadioConfigSessionHolder()
        val session = SettingsRadioConfigSession.Remote(1234)
        holder.activate(session)
        holder.activate(SettingsRadioConfigSession.Inactive)

        assertEquals(session, holder.resolveEntrySession(SettingsRadioConfigSession.Inactive))
    }

    @Test
    fun `inactive provider without prior settings uses a safe local fallback`() {
        val holder = SettingsRadioConfigSessionHolder()

        assertEquals(SettingsRadioConfigSession.Local, holder.resolveEntrySession(SettingsRadioConfigSession.Inactive))
    }

    @Test
    fun `duplicate current route is not pushed again`() {
        assertFalse(shouldAddSettingsRoute(SettingsRoute.DeviceConfiguration, SettingsRoute.DeviceConfiguration))
        assertTrue(shouldAddSettingsRoute(SettingsRoute.DeviceConfiguration, SettingsRoute.ModuleConfiguration))
    }

    private class TrackingViewModel : ViewModel() {
        var wasCleared = false
            private set

        override fun onCleared() {
            wasCleared = true
        }
    }
}
