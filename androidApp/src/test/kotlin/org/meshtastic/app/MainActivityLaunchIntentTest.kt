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
package org.meshtastic.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.OpenableColumns
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.meshtastic.core.di.CoroutineDispatchers
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(application = InlineIoApplication::class, sdk = [34])
class MainActivityLaunchIntentTest {

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test
    fun `recreating the activity does not handle its launch intent again`() {
        val provider = Robolectric.setupContentProvider(CountingFileProvider::class.java, AUTHORITY)
        val controller = Robolectric.buildActivity(MainActivity::class.java, sharedFileIntent("first.txt")).create()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("/first.txt"), provider.queriedPaths)

        controller.recreate()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("/first.txt"), provider.queriedPaths)

        controller.newIntent(sharedFileIntent("second.txt"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("/first.txt", "/second.txt"), provider.queriedPaths)
    }

    private fun sharedFileIntent(name: String) = Intent(Intent.ACTION_VIEW, Uri.parse("content://$AUTHORITY/$name"))

    /** Answers the metadata query for any path and records it; a `.txt` name is refused before any read. */
    class CountingFileProvider : ContentProvider() {
        val queriedPaths = mutableListOf<String>()

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            queriedPaths += uri.path.orEmpty()
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
                addRow(arrayOf<Any?>(uri.lastPathSegment, 1L))
            }
        }

        override fun getType(uri: Uri): String = "text/plain"

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    private companion object {
        const val AUTHORITY = "org.meshtastic.test.launchintent"
    }
}

/**
 * The production Application with Koin, no background init, and IO work run inline, so a shared file's metadata query
 * has happened by the time the main looper is idle. Must not be private: Robolectric instantiates it reflectively.
 */
internal class InlineIoApplication : MeshUtilApplication() {
    override fun onCreate() {
        val configuration =
            Configuration.Builder().setExecutor(SynchronousExecutor()).setTaskExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(this, configuration)
        super.onCreate()
        loadKoinModules(
            module {
                single {
                    CoroutineDispatchers(
                        io = Dispatchers.Unconfined,
                        main = Dispatchers.Main,
                        default = Dispatchers.Default,
                    )
                }
            },
        )
    }

    override fun startBackgroundInit() = Unit

    override fun loadBundledSqlite() = Unit
}
