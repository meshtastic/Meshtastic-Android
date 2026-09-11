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
package org.meshtastic.app.di

import org.koin.plugin.module.dsl.koinApplication
import org.meshtastic.feature.docs.translation.DocTranslationService
import org.meshtastic.feature.docs.translation.NoOpDocTranslator
import org.meshtastic.feature.messaging.translation.MessageTranslationService
import org.meshtastic.feature.messaging.translation.NoOpMessageTranslator
import kotlin.test.Test
import kotlin.test.assertIs

class FdroidBindingWinnerTest {

    @Test
    fun `flavor bindings win over the shared graph`() {
        // The flavor modules are @Configuration, which loads them before the ones listed in @KoinApplication, and
        // Koin is last-wins. KoinVerificationTest only checks definitions exist, never which one survives, so a
        // core-level default added later would silently take these over. Only the Fdroid no-ops are asserted:
        // the Google flavor's MlKitMessageTranslator builds a RemoteModelManager in a field initializer.
        val app = koinApplication<AndroidKoinApp>()
        try {
            val koin = app.koin
            assertIs<NoOpMessageTranslator>(koin.get<MessageTranslationService>())
            assertIs<NoOpDocTranslator>(koin.get<DocTranslationService>())
        } finally {
            app.close()
        }
    }
}
