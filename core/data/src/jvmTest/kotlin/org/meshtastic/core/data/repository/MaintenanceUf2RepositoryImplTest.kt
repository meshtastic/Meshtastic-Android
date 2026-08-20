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
package org.meshtastic.core.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.Source
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.data.datasource.MaintenanceUf2LocalDataSource
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.BootloaderOtaQuirksResponse
import org.meshtastic.core.model.EventFirmwareResponse
import org.meshtastic.core.model.FirmwareReleaseManifest
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.NetworkDeviceLinksResponse
import org.meshtastic.core.model.NetworkFirmwareNightly
import org.meshtastic.core.model.NetworkFirmwareReleases
import org.meshtastic.core.network.MaintenanceUf2RemoteDataSource
import org.meshtastic.core.network.service.ApiService
import org.meshtastic.core.testing.FakeDatabaseProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The manifest bytes here are the REAL `data/maintenanceUf2.json` from `meshtastic/api` (embedded verbatim) and its
 * [org.meshtastic.core.data.repository.MaintenanceUf2RepositoryImpl.EXPECTED_MANIFEST_SHA256] pin — not a synthetic
 * fixture — because the digest check this repository exists to enforce can't be exercised against bytes that don't
 * actually hash to the pin. [TAMPERED_MANIFEST_JSON] is one byte-level edit away from [REAL_MANIFEST_JSON] and is used
 * only to prove a mismatch is rejected.
 */
class MaintenanceUf2RepositoryImplTest {

    /** Only the raw-bytes manifest endpoint is exercised; the other endpoints are never called by this repository. */
    private class FakeApiService(var bytes: ByteArray) : ApiService {
        override suspend fun getDeviceHardware(): List<NetworkDeviceHardware> = error("unused")

        override suspend fun getDeviceLinks(): NetworkDeviceLinksResponse = error("unused")

        override suspend fun getFirmwareReleases(): NetworkFirmwareReleases = error("unused")

        override suspend fun getFirmwareReleaseManifest(manifestUrl: String): FirmwareReleaseManifest = error("unused")

        override suspend fun getNightlyFirmware(): NetworkFirmwareNightly? = error("unused")

        override suspend fun getEventFirmware(): EventFirmwareResponse = error("unused")

        override suspend fun getBootloaderOtaQuirks(): BootloaderOtaQuirksResponse = error("unused")

        override suspend fun getMaintenanceUf2ManifestBytes(): ByteArray = bytes
    }

    /** Serves only `maintenance_uf2.json`, or nothing when [seed] is null (models the asset being absent). */
    private class FakeBundledAssetReader(var seed: ByteArray?) : BundledAssetReader {
        override fun open(name: String): Source? {
            if (name != "maintenance_uf2.json") return null
            return Buffer().write(seed ?: return null)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Real dispatchers + runBlocking, not runTest — mirrors BootloaderOtaQuirksRepositoryImplTest's rationale.
    private val unconfined = Dispatchers.Unconfined
    private val dispatchers = CoroutineDispatchers(main = unconfined, io = unconfined, default = unconfined)

    private lateinit var dbProvider: FakeDatabaseProvider
    private lateinit var local: MaintenanceUf2LocalDataSource
    private lateinit var api: FakeApiService
    private lateinit var seed: FakeBundledAssetReader
    private lateinit var repository: MaintenanceUf2RepositoryImpl

    @BeforeTest
    fun setup() {
        dbProvider = FakeDatabaseProvider()
        local = MaintenanceUf2LocalDataSource(dbProvider, dispatchers)
        api = FakeApiService(ByteArray(0))
        seed = FakeBundledAssetReader(null)
        repository =
            MaintenanceUf2RepositoryImpl(
                remoteDataSource = MaintenanceUf2RemoteDataSource(api, dispatchers),
                localDataSource = local,
                assetReader = seed,
                json = json,
            )
    }

    @AfterTest fun tearDown() = dbProvider.close()

    @Test
    fun getSnapshotSeedsFromBundledJsonWhenCacheIsEmpty() = runBlocking {
        seed.seed = REAL_MANIFEST_BYTES

        val snapshot = repository.getSnapshot()

        assertEquals(14, snapshot.otafixByBoardId.size)
        assertEquals("0.9.2-OTAFIX2.3-BP1.5", snapshot.otafixReleaseTag)
    }

    @Test
    fun getSnapshotDoesNotSeedFromATamperedBundledAsset() = runBlocking {
        seed.seed = TAMPERED_MANIFEST_BYTES

        val snapshot = repository.getSnapshot()

        assertEquals(0, local.count(), "A digest-mismatched bundled asset must never be stored")
        assertEquals(0, snapshot.otafixByBoardId.size, "An unseeded cache resolves to the empty manifest")
    }

    @Test
    fun getSnapshotSeedsOnlyOnce() = runBlocking {
        seed.seed = REAL_MANIFEST_BYTES
        repository.getSnapshot()
        assertEquals(1, local.count())

        // Removing the seed after the cache is populated must not clear it.
        seed.seed = null
        val snapshot = repository.getSnapshot()

        assertEquals(1, local.count())
        assertEquals(14, snapshot.otafixByBoardId.size)
    }

    @Test
    fun getSnapshotIsEmptyWhenNoSeedAndNoCache() = runBlocking {
        val snapshot = repository.getSnapshot()

        assertEquals(0, snapshot.otafixByBoardId.size)
        assertNull(snapshot.erase)
    }

    @Test
    fun reconcileUpdatesCacheFromTheNetwork() = runBlocking {
        api.bytes = REAL_MANIFEST_BYTES
        repository.reconcile()

        val snapshot = repository.getSnapshot()

        assertEquals(14, snapshot.otafixByBoardId.size)
    }

    @Test
    fun reconcileRejectsATamperedNetworkResponse() = runBlocking {
        seed.seed = REAL_MANIFEST_BYTES
        repository.getSnapshot()
        assertEquals(1, local.count())

        api.bytes = TAMPERED_MANIFEST_BYTES
        repository.reconcile()

        assertEquals(1, local.count(), "A digest-mismatched fetch must never overwrite the cache")
        assertEquals(14, repository.getSnapshot().otafixByBoardId.size, "The seeded snapshot must be unchanged")
    }

    @Test
    fun reconcileFailureLeavesCacheUntouched() = runBlocking {
        seed.seed = REAL_MANIFEST_BYTES
        repository.getSnapshot()
        assertEquals(1, local.count())

        api.bytes = ByteArray(0)
        repository.reconcile()

        assertEquals(1, local.count())
        assertEquals(14, repository.getSnapshot().otafixByBoardId.size)
    }

    private companion object {
        val REAL_MANIFEST_JSON =
            """
            {
              "manifestVersion": 1,
              "otafixReleaseTag": "0.9.2-OTAFIX2.3-BP1.5",
              "otafixBase": "https://github.com/meshtastic/Adafruit_nRF52_Bootloader_OTAFIX/releases/download/0.9.2-OTAFIX2.3-BP1.5",
              "erase": {
                "nrf52": {
                  "6.1.1": {
                    "fileName": "nrf_erase2.uf2",
                    "sha256": "4b778a3def19854415db64cb51bfd29c15b11cc46006353dd518f62d09efe3fe",
                    "expectedFirstTargetAddress": 155648
                  },
                  "7.3.0": {
                    "fileName": "nrf_erase_sd7_3.uf2",
                    "sha256": "13941bedce009e61255c37b1524d11ca604e88c38e7588bb8b391e2998da468f",
                    "expectedFirstTargetAddress": 159744
                  }
                },
                "rp2040": {
                  "fileName": "pico_erase.uf2",
                  "sha256": "08aa7d561e8b8bf2f9b061b3506fb4d8f135e832efe0f3ae978241db2da0c853"
                }
              },
              "otafixByBoardId": {
                "HT-n5262": {
                  "otafixBoardSlug": "heltec_t114",
                  "sha256": "ae92d3577cb58dd9b43c9b61ffb9bfffda05b0eca4113a0ec42a37cd8be53b19"
                },
                "MinewSemi-MX25LE01": {
                  "otafixBoardSlug": "minewsemi_mx25le01",
                  "sha256": "e09564fd8dd03fc25d76dcb732a0214c79653da3b130240949b783254d3dfc1b"
                },
                "TRACKER L1": {
                  "otafixBoardSlug": "wio_tracker_l1",
                  "sha256": "70fbce0eda9d70d7bd8a4367057badf5ec310838bf3221370d45a56f04956b9e"
                },
                "WisBlock-RAK4631-Board": {
                  "otafixBoardSlug": "wiscore_rak4631_board",
                  "sha256": "8741bc677a3c24f28422c5ffb80761de7d98a127a3b0191ba6585bf57ce9f305"
                },
                "WisMesh-Tag": {
                  "otafixBoardSlug": "wismesh_tag",
                  "sha256": "96d42e1990e17251e8c625e98a1551cac12c6e29111bc2e59ab7c9fe6dec8758"
                },
                "nRF52840-SeeedSenseCAPSolarP1-v1": {
                  "otafixBoardSlug": "sensecap_solar_p1",
                  "sha256": "9b4bce48c1b4830617715c5619457bce6b21f3079803e35e13433de7701290f5"
                },
                "nRF52840-SeeedXiao-v1": {
                  "otafixBoardSlug": "xiao_nrf52840_ble",
                  "sha256": "ff8a0916e98cceb394fd66590bccc17f63612c11ff56b086ef88bd436c8df67f"
                },
                "nRF52840-SeeedXiaoSense-v1": {
                  "otafixBoardSlug": "xiao_nrf52840_ble_sense",
                  "sha256": "fc233d83a1011419625fcb50b49084578460c25bbc0270374ca176757a3c40da"
                },
                "nRF52840-T1000-E-v1": {
                  "otafixBoardSlug": "t1000_e",
                  "sha256": "5c065e11b8acd5b0cefa9295f98bca1512306cfa478856aa76a871124a904cc4"
                },
                "nRF52840-TEcho-v1": {
                  "otafixBoardSlug": "lilygo_techo",
                  "sha256": "2ddb36188ffe521c270bb2ce8441d742d0fe45325c57e4db6475bf63162a59b0"
                },
                "nRF52840-ThinkNode-M3-v1": {
                  "otafixBoardSlug": "thinknode_m3",
                  "sha256": "bf90979f2f6adc96ef6ca09c280b2ab7e66cb8ce2654fc80da9b20407bfb8708"
                },
                "nRF52840-ThinkNodeM1-v1": {
                  "otafixBoardSlug": "thinknode_m1",
                  "sha256": "aa0721b573c60e0b179274d5a5296bac7a8436faf339cfc03116ebe8a4375795"
                },
                "nRF52840-ThinkNodeM6-v1": {
                  "otafixBoardSlug": "thinknode_m6",
                  "sha256": "aaf94953a540a18f3e48f4cdec0c78290ad3c5f8740aea26fa3b3ce3632a8d4a"
                },
                "nRF52840-promicro": {
                  "otafixBoardSlug": "promicro_nrf52840",
                  "sha256": "46ef3440f151d6f2606075bcd1aa83db25a660da7d25b988aeb47ef350c98794"
                }
              },
              "otafixSupportedTargets": [
                "rak4631",
                "rak_wismeshtag",
                "t-echo",
                "heltec-mesh-node-t114",
                "nrf52_promicro_diy_tcxo",
                "thinknode_m1",
                "thinknode_m3",
                "thinknode_m6",
                "tracker-t1000-e",
                "seeed_wio_tracker_L1",
                "seeed_wio_tracker_L1_eink",
                "seeed_solar_node",
                "seeed_xiao_nrf52840_kit"
              ]
            }
            """
                .trimIndent()

        val TAMPERED_MANIFEST_JSON =
            """
            {
              "manifestVersion": 2,
              "otafixReleaseTag": "0.9.2-OTAFIX2.3-BP1.5",
              "otafixBase": "https://github.com/meshtastic/Adafruit_nRF52_Bootloader_OTAFIX/releases/download/0.9.2-OTAFIX2.3-BP1.5",
              "erase": {
                "nrf52": {
                  "6.1.1": {
                    "fileName": "nrf_erase2.uf2",
                    "sha256": "4b778a3def19854415db64cb51bfd29c15b11cc46006353dd518f62d09efe3fe",
                    "expectedFirstTargetAddress": 155648
                  },
                  "7.3.0": {
                    "fileName": "nrf_erase_sd7_3.uf2",
                    "sha256": "13941bedce009e61255c37b1524d11ca604e88c38e7588bb8b391e2998da468f",
                    "expectedFirstTargetAddress": 159744
                  }
                },
                "rp2040": {
                  "fileName": "pico_erase.uf2",
                  "sha256": "08aa7d561e8b8bf2f9b061b3506fb4d8f135e832efe0f3ae978241db2da0c853"
                }
              },
              "otafixByBoardId": {
                "HT-n5262": {
                  "otafixBoardSlug": "heltec_t114",
                  "sha256": "ae92d3577cb58dd9b43c9b61ffb9bfffda05b0eca4113a0ec42a37cd8be53b19"
                },
                "MinewSemi-MX25LE01": {
                  "otafixBoardSlug": "minewsemi_mx25le01",
                  "sha256": "e09564fd8dd03fc25d76dcb732a0214c79653da3b130240949b783254d3dfc1b"
                },
                "TRACKER L1": {
                  "otafixBoardSlug": "wio_tracker_l1",
                  "sha256": "70fbce0eda9d70d7bd8a4367057badf5ec310838bf3221370d45a56f04956b9e"
                },
                "WisBlock-RAK4631-Board": {
                  "otafixBoardSlug": "wiscore_rak4631_board",
                  "sha256": "8741bc677a3c24f28422c5ffb80761de7d98a127a3b0191ba6585bf57ce9f305"
                },
                "WisMesh-Tag": {
                  "otafixBoardSlug": "wismesh_tag",
                  "sha256": "96d42e1990e17251e8c625e98a1551cac12c6e29111bc2e59ab7c9fe6dec8758"
                },
                "nRF52840-SeeedSenseCAPSolarP1-v1": {
                  "otafixBoardSlug": "sensecap_solar_p1",
                  "sha256": "9b4bce48c1b4830617715c5619457bce6b21f3079803e35e13433de7701290f5"
                },
                "nRF52840-SeeedXiao-v1": {
                  "otafixBoardSlug": "xiao_nrf52840_ble",
                  "sha256": "ff8a0916e98cceb394fd66590bccc17f63612c11ff56b086ef88bd436c8df67f"
                },
                "nRF52840-SeeedXiaoSense-v1": {
                  "otafixBoardSlug": "xiao_nrf52840_ble_sense",
                  "sha256": "fc233d83a1011419625fcb50b49084578460c25bbc0270374ca176757a3c40da"
                },
                "nRF52840-T1000-E-v1": {
                  "otafixBoardSlug": "t1000_e",
                  "sha256": "5c065e11b8acd5b0cefa9295f98bca1512306cfa478856aa76a871124a904cc4"
                },
                "nRF52840-TEcho-v1": {
                  "otafixBoardSlug": "lilygo_techo",
                  "sha256": "2ddb36188ffe521c270bb2ce8441d742d0fe45325c57e4db6475bf63162a59b0"
                },
                "nRF52840-ThinkNode-M3-v1": {
                  "otafixBoardSlug": "thinknode_m3",
                  "sha256": "bf90979f2f6adc96ef6ca09c280b2ab7e66cb8ce2654fc80da9b20407bfb8708"
                },
                "nRF52840-ThinkNodeM1-v1": {
                  "otafixBoardSlug": "thinknode_m1",
                  "sha256": "aa0721b573c60e0b179274d5a5296bac7a8436faf339cfc03116ebe8a4375795"
                },
                "nRF52840-ThinkNodeM6-v1": {
                  "otafixBoardSlug": "thinknode_m6",
                  "sha256": "aaf94953a540a18f3e48f4cdec0c78290ad3c5f8740aea26fa3b3ce3632a8d4a"
                },
                "nRF52840-promicro": {
                  "otafixBoardSlug": "promicro_nrf52840",
                  "sha256": "46ef3440f151d6f2606075bcd1aa83db25a660da7d25b988aeb47ef350c98794"
                }
              },
              "otafixSupportedTargets": [
                "rak4631",
                "rak_wismeshtag",
                "t-echo",
                "heltec-mesh-node-t114",
                "nrf52_promicro_diy_tcxo",
                "thinknode_m1",
                "thinknode_m3",
                "thinknode_m6",
                "tracker-t1000-e",
                "seeed_wio_tracker_L1",
                "seeed_wio_tracker_L1_eink",
                "seeed_solar_node",
                "seeed_xiao_nrf52840_kit"
              ]
            }
            """
                .trimIndent()

        // The committed data/maintenanceUf2.json (and the byte-identical bundled asset) end with a trailing
        // newline; .trimIndent() on the literal above does not produce one, so it's appended explicitly to match
        // the exact bytes EXPECTED_MANIFEST_SHA256 is pinned against.
        val REAL_MANIFEST_BYTES = "$REAL_MANIFEST_JSON\n".encodeToByteArray()
        val TAMPERED_MANIFEST_BYTES = "$TAMPERED_MANIFEST_JSON\n".encodeToByteArray()
    }
}
