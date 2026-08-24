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
package org.meshtastic.core.domain.usecase.settings

import org.meshtastic.core.repository.StoredSecurityKeys
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportSecurityConfigUseCaseTest {

    private lateinit var useCase: ImportSecurityConfigUseCase

    @BeforeTest
    fun setUp() {
        useCase = ImportSecurityConfigUseCase()
    }

    @Test
    fun `invoke decodes a valid backup into a SecurityConfig`() {
        val stored = StoredSecurityKeys(publicKeyBase64 = "AQID", privateKeyBase64 = "BAUG", timestamp = 123L)

        val result = useCase(stored)

        assertTrue(result.isSuccess)
        val config = result.getOrThrow()
        assertEquals(byteArrayOf(1, 2, 3).toList(), config.public_key.toByteArray().toList())
        assertEquals(byteArrayOf(4, 5, 6).toList(), config.private_key.toByteArray().toList())
    }
}
