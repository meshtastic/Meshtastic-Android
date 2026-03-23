/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.settings

import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.domain.usecase.settings.ExportDataUseCase
import org.meshtastic.core.domain.usecase.settings.IsOtaCapableUseCase
import org.meshtastic.core.domain.usecase.settings.MeshLocationUseCase
import org.meshtastic.core.domain.usecase.settings.SetAppIntroCompletedUseCase
import org.meshtastic.core.domain.usecase.settings.SetDatabaseCacheLimitUseCase
import org.meshtastic.core.domain.usecase.settings.SetLocaleUseCase
import org.meshtastic.core.domain.usecase.settings.SetMeshLogSettingsUseCase
import org.meshtastic.core.domain.usecase.settings.SetNotificationSettingsUseCase
import org.meshtastic.core.domain.usecase.settings.SetProvideLocationUseCase
import org.meshtastic.core.domain.usecase.settings.SetThemeUseCase
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.AppPreferences
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.MeshLogPrefs
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.proto.LocalConfig
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var radioController: FakeRadioController
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val uiPrefs: UiPrefs = mock(MockMode.autofill)
    private val buildConfigProvider: BuildConfigProvider = mock(MockMode.autofill)
    private val databaseManager: DatabaseManager = mock(MockMode.autofill)
    private val meshLogPrefs: MeshLogPrefs = mock(MockMode.autofill)
    private val notificationPrefs: NotificationPrefs = mock(MockMode.autofill)
    private val meshLogRepository: MeshLogRepository = mock(MockMode.autofill)
    private val fileService: FileService = mock(MockMode.autofill)

    @BeforeTest
    fun setUp() {
        nodeRepository = FakeNodeRepository()
        radioController = FakeRadioController()

        // INDIVIDUAL BLOCKS FOR MOKKERY
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig())
        every { databaseManager.cacheLimit } returns MutableStateFlow(100)
        every { meshLogPrefs.retentionDays } returns MutableStateFlow(30)
        every { meshLogPrefs.loggingEnabled } returns MutableStateFlow(true)
        every { notificationPrefs.messagesEnabled } returns MutableStateFlow(true)
        every { notificationPrefs.nodeEventsEnabled } returns MutableStateFlow(true)
        every { notificationPrefs.lowBatteryEnabled } returns MutableStateFlow(true)

        val isOtaCapableUseCase: IsOtaCapableUseCase = mock(MockMode.autofill)
        every { isOtaCapableUseCase() } returns flowOf(true)

        val setThemeUseCase = SetThemeUseCase(uiPrefs)
        val setLocaleUseCase = SetLocaleUseCase(uiPrefs)
        val setAppIntroCompletedUseCase = SetAppIntroCompletedUseCase(uiPrefs)

        val appPreferences: AppPreferences = mock(MockMode.autofill)
        every { appPreferences.ui } returns uiPrefs
        val setProvideLocationUseCase = SetProvideLocationUseCase(uiPrefs)

        val setDatabaseCacheLimitUseCase = SetDatabaseCacheLimitUseCase(databaseManager)
        val setMeshLogSettingsUseCase = SetMeshLogSettingsUseCase(meshLogRepository, meshLogPrefs)
        val setNotificationSettingsUseCase = SetNotificationSettingsUseCase(notificationPrefs)
        val meshLocationUseCase = MeshLocationUseCase(radioController)
        val exportDataUseCase = ExportDataUseCase(nodeRepository, meshLogRepository)

        viewModel =
            SettingsViewModel(
                radioConfigRepository = radioConfigRepository,
                radioController = radioController,
                nodeRepository = nodeRepository,
                uiPrefs = uiPrefs,
                buildConfigProvider = buildConfigProvider,
                databaseManager = databaseManager,
                meshLogPrefs = meshLogPrefs,
                notificationPrefs = notificationPrefs,
                setThemeUseCase = setThemeUseCase,
                setLocaleUseCase = setLocaleUseCase,
                setAppIntroCompletedUseCase = setAppIntroCompletedUseCase,
                setProvideLocationUseCase = setProvideLocationUseCase,
                setDatabaseCacheLimitUseCase = setDatabaseCacheLimitUseCase,
                setMeshLogSettingsUseCase = setMeshLogSettingsUseCase,
                setNotificationSettingsUseCase = setNotificationSettingsUseCase,
                meshLocationUseCase = meshLocationUseCase,
                exportDataUseCase = exportDataUseCase,
                isOtaCapableUseCase = isOtaCapableUseCase,
                fileService = fileService,
            )
    }

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
    }

    @Test
    fun `isConnected flow emits updates using Turbine`() = runTest {
        viewModel.isConnected.test {
            // Initial state from FakeRadioController (default Disconnected)
            assertEquals(false, awaitItem())

            radioController.setConnectionState(ConnectionState.Connected)
            assertEquals(true, awaitItem())

            radioController.setConnectionState(ConnectionState.Disconnected)
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `test property based bounds for mesh log retention days`() = runTest {
        checkAll(Arb.int(-100, 500)) { input ->
            viewModel.setMeshLogRetentionDays(input)
            viewModel.meshLogRetentionDays.value shouldBeInRange -1..365
        }
    }
}
