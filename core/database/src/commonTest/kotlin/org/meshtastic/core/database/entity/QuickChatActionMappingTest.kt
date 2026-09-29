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
package org.meshtastic.core.database.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import org.meshtastic.core.model.QuickChatAction as QuickChatActionModel

class QuickChatActionMappingTest {

    @Test
    fun `each mode maps to the entity mode of the same name`() {
        QuickChatActionModel.Mode.entries.forEach { mode ->
            val model = QuickChatActionModel(uuid = 7L, name = "Greeting", message = "Hello", mode = mode, position = 3)

            assertEquals(
                QuickChatAction(
                    uuid = 7L,
                    name = "Greeting",
                    message = "Hello",
                    mode = QuickChatAction.Mode.valueOf(mode.name),
                    position = 3,
                ),
                model.asEntity(),
            )
        }
    }

    @Test
    fun `an entity read back maps to the model it was written from`() {
        QuickChatActionModel.Mode.entries.forEach { mode ->
            val model = QuickChatActionModel(uuid = 7L, name = "Greeting", message = "Hello", mode = mode, position = 3)

            assertEquals(model, model.asEntity().asExternalModel())
        }
    }
}
