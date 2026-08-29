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
package org.meshtastic.feature.map.layers

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.testing.FakeMapPrefs
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LayerOpacityStoreTest {

    private val prefs = FakeMapPrefs()

    private fun store(): LayerOpacityStore {
        val dispatcher = UnconfinedTestDispatcher()
        return LayerOpacityStore(prefs, CoroutineDispatchers(dispatcher, dispatcher, dispatcher))
    }

    @Test
    fun `an untouched layer reads as opaque`() = runTest {
        assertEquals(1f, store().opacity.value.opacityOf("hillshade"))
    }

    @Test
    fun `a set opacity is readable back`() = runTest {
        val store = store()

        store.setOpacity("hillshade", 0.4f)

        assertEquals(0.4f, store.opacity.value.opacityOf("hillshade"))
    }

    @Test
    fun `setting one layer leaves the others alone`() = runTest {
        val store = store()

        store.setOpacity("hillshade", 0.4f)
        store.setOpacity("file:///a.kml", 0.7f)

        assertEquals(0.4f, store.opacity.value.opacityOf("hillshade"))
        assertEquals(0.7f, store.opacity.value.opacityOf("file:///a.kml"))
    }

    @Test
    fun `restoring a layer to opaque drops it from storage`() = runTest {
        val store = store()

        store.setOpacity("hillshade", 0.4f)
        store.setOpacity("hillshade", 1f)

        assertEquals(emptySet<String>(), prefs.layerOpacity.value)
        assertEquals(1f, store.opacity.value.opacityOf("hillshade"))
    }

    @Test
    fun `a value already persisted is visible without any set`() = runTest {
        prefs.layerOpacity.value = setOf("weather|:|0.25")

        assertEquals(0.25f, store().opacity.value.opacityOf("weather"))
    }
}
