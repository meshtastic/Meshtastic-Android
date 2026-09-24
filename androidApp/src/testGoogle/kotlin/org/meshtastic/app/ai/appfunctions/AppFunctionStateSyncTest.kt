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
package org.meshtastic.app.ai.appfunctions

import org.meshtastic.app.ai.appfunctions.AppFunctionStateSync.Companion.pendingWrites
import kotlin.test.Test
import kotlin.test.assertEquals

class AppFunctionStateSyncTest {

    private val desired =
        listOf(AppFunctionStateSync.SEND_MESSAGE_ID to true, AppFunctionStateSync.GET_NODE_LIST_ID to false)

    @Test
    fun `unreadable system state writes every toggle`() {
        assertEquals(desired, pendingWrites(desired, actual = null))
    }

    @Test
    fun `matching system state writes nothing`() {
        val actual = mapOf(AppFunctionStateSync.SEND_MESSAGE_ID to true, AppFunctionStateSync.GET_NODE_LIST_ID to false)

        assertEquals(emptyList(), pendingWrites(desired, actual))
    }

    @Test
    fun `only drifted toggles are written`() {
        val actual = mapOf(AppFunctionStateSync.SEND_MESSAGE_ID to true, AppFunctionStateSync.GET_NODE_LIST_ID to true)

        assertEquals(listOf(AppFunctionStateSync.GET_NODE_LIST_ID to false), pendingWrites(desired, actual))
    }

    /** A function the system has not indexed yet is absent from the read-back, so it must still be written. */
    @Test
    fun `unindexed toggles are written`() {
        val actual = mapOf(AppFunctionStateSync.SEND_MESSAGE_ID to true)

        assertEquals(listOf(AppFunctionStateSync.GET_NODE_LIST_ID to false), pendingWrites(desired, actual))
    }

    @Test
    fun `toggle ids match the generated function ids`() {
        assertEquals(MeshtasticAppFunctionService.FUNCTION_ID_SEND_MESSAGE, AppFunctionStateSync.SEND_MESSAGE_ID)
        assertEquals(MeshtasticAppFunctionService.FUNCTION_ID_GET_MESH_STATUS, AppFunctionStateSync.GET_MESH_STATUS_ID)
        assertEquals(MeshtasticAppFunctionService.FUNCTION_ID_GET_NODE_LIST, AppFunctionStateSync.GET_NODE_LIST_ID)
        assertEquals(
            MeshtasticAppFunctionService.FUNCTION_ID_GET_CHANNEL_INFO,
            AppFunctionStateSync.GET_CHANNEL_INFO_ID,
        )
        assertEquals(
            MeshtasticAppFunctionService.FUNCTION_ID_GET_DEVICE_STATUS,
            AppFunctionStateSync.GET_DEVICE_STATUS_ID,
        )
        assertEquals(
            MeshtasticAppFunctionService.FUNCTION_ID_GET_NODE_DETAILS,
            AppFunctionStateSync.GET_NODE_DETAILS_ID,
        )
        assertEquals(
            MeshtasticAppFunctionService.FUNCTION_ID_GET_MESH_METRICS,
            AppFunctionStateSync.GET_MESH_METRICS_ID,
        )
        assertEquals(
            MeshtasticAppFunctionService.FUNCTION_ID_GET_RECENT_MESSAGES,
            AppFunctionStateSync.GET_RECENT_MESSAGES_ID,
        )
        assertEquals(
            MeshtasticAppFunctionService.FUNCTION_ID_GET_UNREAD_SUMMARY,
            AppFunctionStateSync.GET_UNREAD_SUMMARY_ID,
        )
    }
}
