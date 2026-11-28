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
package org.meshtastic.core.prefs.label

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.asUiDataStore
import org.meshtastic.core.prefs.store.PrefsStore
import org.meshtastic.core.prefs.store.asPrefsStore
import org.meshtastic.core.prefs.store.stringPrefsKey
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

/** design#185 item 5 (remember the printer) and the 40 mm default label length. */
class LabelPrintPrefsImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher)
    private lateinit var tmpDir: Path
    private lateinit var store: PrefsStore

    @BeforeTest
    fun setup() {
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "labelPrintPrefsTest-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(tmpDir)
        store =
            PreferenceDataStoreFactory.createWithPath(scope = testScope, produceFile = { tmpDir / "ui.preferences_pb" })
                .asPrefsStore()
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    private fun prefs() = LabelPrintPrefsImpl(store.asUiDataStore(), dispatchers)

    @Test
    fun `a label length never set reads as 40 mm and no printer is remembered`() = testScope.runTest {
        val prefs = prefs()
        advanceUntilIdle()

        assertEquals(40 to null, prefs.labelLengthMm.value to prefs.printerAddress.value)
    }

    @Test
    fun `the label length and printer set are read back by a later instance`() = testScope.runTest {
        prefs().apply {
            setLabelLengthMm(30)
            setPrinterAddress("AA:BB:CC:DD:EE:FF")
        }
        advanceUntilIdle()

        val later = prefs()
        advanceUntilIdle()

        assertEquals(30 to "AA:BB:CC:DD:EE:FF", later.labelLengthMm.value to later.printerAddress.value)
    }

    @Test
    fun `forgetting the printer removes its stored address`() = testScope.runTest {
        val prefs = prefs()
        prefs.setPrinterAddress("AA:BB:CC:DD:EE:FF")
        advanceUntilIdle()

        prefs.setPrinterAddress(null)
        advanceUntilIdle()

        assertFalse(stringPrefsKey("label-print-printer-address") in store.data.first())
        assertEquals(null, prefs.printerAddress.value)
    }
}
