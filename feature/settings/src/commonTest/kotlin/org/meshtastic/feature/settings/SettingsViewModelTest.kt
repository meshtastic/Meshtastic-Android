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
package org.meshtastic.feature.settings

import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.domain.usecase.settings.ExportDataUseCase
import org.meshtastic.core.domain.usecase.settings.IsOtaCapableUseCase
import org.meshtastic.core.domain.usecase.settings.MeshLocationUseCase
import org.meshtastic.core.domain.usecase.settings.SetAppIntroCompletedUseCase
import org.meshtastic.core.domain.usecase.settings.SetContrastLevelUseCase
import org.meshtastic.core.domain.usecase.settings.SetDatabaseCacheLimitUseCase
import org.meshtastic.core.domain.usecase.settings.SetLocaleUseCase
import org.meshtastic.core.domain.usecase.settings.SetMeshLogSettingsUseCase
import org.meshtastic.core.domain.usecase.settings.SetNotificationSettingsUseCase
import org.meshtastic.core.domain.usecase.settings.SetProvideLocationUseCase
import org.meshtastic.core.domain.usecase.settings.SetThemeUseCase
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.testing.FakeAppPreferences
import org.meshtastic.core.testing.FakeDatabaseManager
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeNotificationPrefs
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.proto.LocalConfig
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: SettingsViewModel
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var radioController: FakeRadioController
    private lateinit var appPreferences: FakeAppPreferences
    private lateinit var meshLogRepository: FakeMeshLogRepository
    private lateinit var databaseManager: FakeDatabaseManager
    private lateinit var notificationPrefs: FakeNotificationPrefs
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val buildConfigProvider: BuildConfigProvider = mock(MockMode.autofill)
    private val fileService: FileService = mock(MockMode.autofill)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        nodeRepository = FakeNodeRepository()
        radioController = FakeRadioController()
        appPreferences = FakeAppPreferences()
        meshLogRepository = FakeMeshLogRepository()
        databaseManager = FakeDatabaseManager()
        notificationPrefs = FakeNotificationPrefs()

        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig())
        every { buildConfigProvider.versionName } returns "3.0.0-test"

        val isOtaCapableUseCase: IsOtaCapableUseCase = mock(MockMode.autofill)
        every { isOtaCapableUseCase() } returns flowOf(true)

        val uiPrefs = appPreferences.ui
        val setThemeUseCase = SetThemeUseCase(uiPrefs)
        val setContrastLevelUseCase = SetContrastLevelUseCase(uiPrefs)
        val setLocaleUseCase = SetLocaleUseCase(uiPrefs)
        val setAppIntroCompletedUseCase = SetAppIntroCompletedUseCase(uiPrefs)
        val setProvideLocationUseCase = SetProvideLocationUseCase(uiPrefs)
        val setDatabaseCacheLimitUseCase = SetDatabaseCacheLimitUseCase(databaseManager)
        val setMeshLogSettingsUseCase = SetMeshLogSettingsUseCase(meshLogRepository, appPreferences.meshLog)
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
                meshLogPrefs = appPreferences.meshLog,
                notificationPrefs = notificationPrefs,
                setThemeUseCase = setThemeUseCase,
                setContrastLevelUseCase = setContrastLevelUseCase,
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

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
        assertEquals("3.0.0-test", viewModel.appVersionName)
    }

    @Test
    fun `isConnected flow emits updates using Turbine`() = runTest {
        viewModel.isConnected.test {
            expectMostRecentItem() shouldBe true // Default in FakeRadioController is Connected (true)

            radioController.setConnectionState(ConnectionState.Disconnected)
            runCurrent()
            expectMostRecentItem() shouldBe false

            radioController.setConnectionState(ConnectionState.Connected)
            runCurrent()
            expectMostRecentItem() shouldBe true
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isOtaCapable flow works`() = runTest {
        viewModel.isOtaCapable.test {
            expectMostRecentItem() shouldBe true
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `notification settings update prefs`() = runTest {
        viewModel.setMessagesEnabled(false)
        notificationPrefs.messagesEnabled.value shouldBe false

        viewModel.setNodeEventsEnabled(false)
        notificationPrefs.nodeEventsEnabled.value shouldBe false

        viewModel.setLowBatteryEnabled(false)
        notificationPrefs.lowBatteryEnabled.value shouldBe false
    }

    @Test
    fun `mesh log logging setting updates prefs`() = runTest {
        viewModel.setMeshLogLoggingEnabled(false)
        appPreferences.meshLog.loggingEnabled.value shouldBe false

        viewModel.setMeshLogLoggingEnabled(true)
        appPreferences.meshLog.loggingEnabled.value shouldBe true
    }

    @Test
    fun `unlockExcludedModules updates state`() = runTest {
        viewModel.excludedModulesUnlocked.value shouldBe false
        viewModel.unlockExcludedModules()
        viewModel.excludedModulesUnlocked.value shouldBe true
    }

    @Test
    fun `provideLocation flows based on current node`() = runTest {
        val myNodeNum = 456
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = myNodeNum))
        runCurrent()

        viewModel.provideLocation.test {
            expectMostRecentItem() shouldBe true // Default in FakeUiPrefs is true

            appPreferences.ui.setShouldProvideNodeLocation(myNodeNum, false)
            runCurrent()
            expectMostRecentItem() shouldBe false
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `meshLocationUseCase calls work`() {
        viewModel.startProvidingLocation()
        radioController.startProvideLocationCalled shouldBe true

        viewModel.stopProvidingLocation()
        radioController.stopProvideLocationCalled shouldBe true
    }

    @Test
    fun `test property based bounds for mesh log retention days`() = runTest {
        checkAll(Arb.int(-100, 500)) { input ->
            viewModel.setMeshLogRetentionDays(input)
            viewModel.meshLogRetentionDays.value shouldBeInRange -1..365
        }
    }

    @Test
    fun `setTheme updates prefs`() = runTest {
        viewModel.setTheme(2)
        appPreferences.ui.theme.value shouldBe 2
    }

    @Test
    fun `setLocale updates prefs`() = runTest {
        viewModel.setLocale("fr")
        appPreferences.ui.locale.value shouldBe "fr"
    }

    @Test
    fun `showAppIntro updates prefs`() = runTest {
        viewModel.showAppIntro()
        appPreferences.ui.appIntroCompleted.value shouldBe false
    }

    @Test
    fun `setProvideLocation updates prefs for current node`() = runTest {
        val myNodeNum = 123
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = myNodeNum))

        viewModel.setProvideLocation(true)
        appPreferences.ui.shouldProvideNodeLocation(myNodeNum).value shouldBe true

        viewModel.setProvideLocation(false)
        appPreferences.ui.shouldProvideNodeLocation(myNodeNum).value shouldBe false
    }

    @Test
    fun `setDbCacheLimit updates manager`() = runTest {
        viewModel.setDbCacheLimit(200)
        databaseManager.cacheLimit.value shouldBe 10 // Clamped to MAX_CACHE_LIMIT
    }
}
