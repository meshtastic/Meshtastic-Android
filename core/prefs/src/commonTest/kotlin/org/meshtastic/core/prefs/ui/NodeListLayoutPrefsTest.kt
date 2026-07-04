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
package org.meshtastic.core.prefs.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.NodeListDensity
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class NodeListLayoutPrefsTest {
    private lateinit var tmpDir: Path
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: UiPrefsImpl
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @BeforeTest
    fun setup() {
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "nodeLayoutPrefsTest-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(tmpDir)
        dataStore =
            PreferenceDataStoreFactory.createWithPath(
                scope = testScope,
                produceFile = { tmpDir / "test.preferences_pb" },
            )
        val dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher)
        prefs = UiPrefsImpl(dataStore, dispatchers)
    }

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    // Density defaults and round-trip

    @Test
    fun `nodeListDensity defaults to COMPLETE`() =
        testScope.runTest { assertEquals(NodeListDensity.COMPLETE.name, prefs.nodeListDensity.value) }

    @Test
    fun `setNodeListDensity persists COMPACT`() = testScope.runTest {
        prefs.setNodeListDensity(NodeListDensity.COMPACT.name)
        assertEquals(NodeListDensity.COMPACT.name, prefs.nodeListDensity.value)
    }

    // Boolean toggle defaults (all true except lastHeardIsRelative)

    @Test fun `shouldShowPower defaults to true`() = testScope.runTest { assertTrue(prefs.shouldShowPower.value) }

    @Test
    fun `shouldShowLastHeard defaults to true`() = testScope.runTest { assertTrue(prefs.shouldShowLastHeard.value) }

    @Test
    fun `lastHeardIsRelative defaults to false`() = testScope.runTest { assertFalse(prefs.lastHeardIsRelative.value) }

    @Test fun `shouldShowLocation defaults to true`() = testScope.runTest { assertTrue(prefs.shouldShowLocation.value) }

    @Test fun `shouldShowHops defaults to true`() = testScope.runTest { assertTrue(prefs.shouldShowHops.value) }

    @Test fun `shouldShowSignal defaults to true`() = testScope.runTest { assertTrue(prefs.shouldShowSignal.value) }

    @Test fun `shouldShowChannel defaults to true`() = testScope.runTest { assertTrue(prefs.shouldShowChannel.value) }

    @Test fun `shouldShowRole defaults to true`() = testScope.runTest { assertTrue(prefs.shouldShowRole.value) }

    @Test
    fun `shouldShowTelemetry defaults to true`() = testScope.runTest { assertTrue(prefs.shouldShowTelemetry.value) }

    // Setters round-trip

    @Test
    fun `setShouldShowPower persists false`() = testScope.runTest {
        prefs.setShouldShowPower(false)
        assertFalse(prefs.shouldShowPower.value)
    }

    @Test
    fun `setShouldShowLastHeard persists false`() = testScope.runTest {
        prefs.setShouldShowLastHeard(false)
        assertFalse(prefs.shouldShowLastHeard.value)
    }

    @Test
    fun `setLastHeardIsRelative persists true`() = testScope.runTest {
        prefs.setLastHeardIsRelative(true)
        assertTrue(prefs.lastHeardIsRelative.value)
    }

    @Test
    fun `setShouldShowLocation persists false`() = testScope.runTest {
        prefs.setShouldShowLocation(false)
        assertFalse(prefs.shouldShowLocation.value)
    }

    @Test
    fun `setShouldShowHops persists false`() = testScope.runTest {
        prefs.setShouldShowHops(false)
        assertFalse(prefs.shouldShowHops.value)
    }

    @Test
    fun `setShouldShowSignal persists false`() = testScope.runTest {
        prefs.setShouldShowSignal(false)
        assertFalse(prefs.shouldShowSignal.value)
    }

    @Test
    fun `setShouldShowChannel persists false`() = testScope.runTest {
        prefs.setShouldShowChannel(false)
        assertFalse(prefs.shouldShowChannel.value)
    }

    @Test
    fun `setShouldShowRole persists false`() = testScope.runTest {
        prefs.setShouldShowRole(false)
        assertFalse(prefs.shouldShowRole.value)
    }

    @Test
    fun `setShouldShowTelemetry persists false`() = testScope.runTest {
        prefs.setShouldShowTelemetry(false)
        assertFalse(prefs.shouldShowTelemetry.value)
    }
}
