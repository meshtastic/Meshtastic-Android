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
package org.meshtastic.core.ui.util

import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.util.getChannelReplacementList
import org.meshtastic.core.model.util.normalizeReplacementSettings
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.meshtastic.core.model.Channel as ModelChannel

/**
 * Coverage for [getChannelReplacementList]. The REPLACE helper must emit an authoritative slot list for QR imports:
 * every imported index becomes a write (PRIMARY at 0, SECONDARY thereafter), and any trailing slots present in the
 * cached set are emitted as DISABLED so the radio stops using them. Critically, positions where the cache already
 * matches the import are NOT skipped — the diff-skip was the source of stale channels.
 */
class ProtoExtensionsTest {
    @Test
    fun index_zero_emits_primary_with_new_settings_even_when_unchanged_from_old() {
        val same =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Main"
                    wb.psk = byteArrayOf(1, 2, 3).toByteString()
                }
                .build()

        val result = getChannelReplacementList(new = listOf(same), currentSettings = listOf(same))

        assertEquals(1, result.size)
        assertEquals(Channel.Role.PRIMARY, result.single().role)
        assertEquals(0, result.single().index)
        assertEquals(same, result.single().settings)
    }

    @Test
    fun secondary_indices_emit_secondary_with_new_settings_even_when_unchanged_from_old() {
        val primary = ChannelSettings.Builder().also { wb -> wb.name = "Main" }.build()
        val secondary = ChannelSettings.Builder().also { wb -> wb.name = "Chat" }.build()

        val result =
            getChannelReplacementList(new = listOf(primary, secondary), currentSettings = listOf(primary, secondary))

        assertEquals(2, result.size)
        assertEquals(Channel.Role.PRIMARY, result[0].role)
        assertEquals(primary, result[0].settings)
        assertEquals(Channel.Role.SECONDARY, result[1].role)
        assertEquals(1, result[1].index)
        assertEquals(secondary, result[1].settings)
    }

    @Test
    fun old_trailing_indices_beyond_new_are_emitted_as_disabled_with_empty_settings() {
        val primary = ChannelSettings.Builder().also { wb -> wb.name = "Main" }.build()

        val result =
            getChannelReplacementList(
                new = listOf(primary),
                currentSettings = listOf(primary, ChannelSettings.Builder().also { wb -> wb.name = "Old" }.build()),
            )

        // index 0 PRIMARY (new), index 1 DISABLED (trailing old slot)
        assertEquals(2, result.size)
        assertEquals(Channel.Role.PRIMARY, result[0].role)
        assertEquals(primary, result[0].settings)
        assertEquals(Channel.Role.DISABLED, result[1].role)
        assertEquals(1, result[1].index)
        assertEquals(ChannelSettings.Builder().build(), result[1].settings)
    }

    @Test
    fun empty_new_and_empty_old_produces_empty_list() {
        val result = getChannelReplacementList(new = emptyList(), currentSettings = emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun empty_new_with_non_empty_current_emits_disabled_for_every_current_index() {
        val currentSettings =
            listOf(
                ChannelSettings.Builder().also { wb -> wb.name = "A" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "B" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "C" }.build(),
            )

        val result = getChannelReplacementList(new = emptyList(), currentSettings = currentSettings)

        assertEquals(3, result.size)
        result.forEachIndexed { i, channel ->
            assertEquals(Channel.Role.DISABLED, channel.role, "index $i should be DISABLED")
            assertEquals(i, channel.index)
            assertEquals(ChannelSettings.Builder().build(), channel.settings, "index $i should carry empty settings")
        }
    }

    @Test
    fun single_entry_new_with_multi_entry_current_emits_primary_then_disabled_trailing() {
        val newPrimary = ChannelSettings.Builder().also { wb -> wb.name = "Imported" }.build()
        val currentSettings =
            listOf(
                ChannelSettings.Builder().also { wb -> wb.name = "CurrentPrimary" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "CurrentSecondary" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "CurrentTertiary" }.build(),
            )

        val result = getChannelReplacementList(new = listOf(newPrimary), currentSettings = currentSettings)

        assertEquals(3, result.size)
        assertEquals(Channel.Role.PRIMARY, result[0].role)
        assertEquals(0, result[0].index)
        assertEquals(newPrimary, result[0].settings)
        assertEquals(Channel.Role.DISABLED, result[1].role)
        assertEquals(Channel.Role.DISABLED, result[2].role)
        assertEquals(ChannelSettings.Builder().build(), result[1].settings)
        assertEquals(ChannelSettings.Builder().build(), result[2].settings)
    }

    @Test
    fun new_larger_than_old_emits_primary_plus_secondaries_for_every_new_index() {
        val primary = ChannelSettings.Builder().also { wb -> wb.name = "Main" }.build()
        val secondaryA = ChannelSettings.Builder().also { wb -> wb.name = "Chat" }.build()
        val secondaryB = ChannelSettings.Builder().also { wb -> wb.name = "Data" }.build()

        val result =
            getChannelReplacementList(new = listOf(primary, secondaryA, secondaryB), currentSettings = listOf(primary))

        assertEquals(3, result.size)
        assertEquals(Channel.Role.PRIMARY, result[0].role)
        assertEquals(0, result[0].index)
        assertEquals(primary, result[0].settings)
        assertEquals(Channel.Role.SECONDARY, result[1].role)
        assertEquals(1, result[1].index)
        assertEquals(secondaryA, result[1].settings)
        assertEquals(Channel.Role.SECONDARY, result[2].role)
        assertEquals(2, result[2].index)
        assertEquals(secondaryB, result[2].settings)
    }

    @Test
    fun replacement_list_rejects_minimum_slot_count_above_maximum_slot_count() {
        assertFailsWith<IllegalArgumentException> {
            getChannelReplacementList(
                new = listOf(ChannelSettings.Builder().also { wb -> wb.name = "Main" }.build()),
                currentSettings = emptyList(),
                minimumSlotCount = 2,
                maximumSlotCount = 1,
            )
        }
    }

    @Test
    fun import_writes_all_eight_slots_with_replacement_roles() = runTest {
        val radioController = FakeRadioController()
        val radioConfigRepository = FakeRadioConfigRepository()
        val oldSettings =
            listOf(
                ChannelSettings.Builder().also { wb -> wb.name = "Old Primary" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "Old Secondary" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "Old Tertiary" }.build(),
            )
        val importedSettings =
            listOf(
                ChannelSettings.Builder().also { wb -> wb.name = "Imported" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "Private" }.build(),
            )
        radioConfigRepository.setChannelSet(ChannelSet.Builder().also { wb -> wb.settings = oldSettings }.build())

        importChannelSet(
            channelSet = ChannelSet.Builder().also { wb -> wb.settings = importedSettings }.build(),
            radioController = radioController,
            radioConfigRepository = radioConfigRepository,
        )

        assertEquals((0..7).toList(), radioController.localChannels.map { it.index })
        assertEquals(
            listOf(
                Channel.Role.PRIMARY,
                Channel.Role.SECONDARY,
                Channel.Role.DISABLED,
                Channel.Role.DISABLED,
                Channel.Role.DISABLED,
                Channel.Role.DISABLED,
                Channel.Role.DISABLED,
                Channel.Role.DISABLED,
            ),
            radioController.localChannels.map { it.role },
        )
        assertEquals(
            importedSettings + List(6) { ChannelSettings.Builder().build() },
            radioController.localChannels.map { it.settings ?: error("Channel write omitted settings") },
        )
        assertEquals(importedSettings, radioConfigRepository.currentChannelSet.settings)
    }

    @Test
    fun import_leaves_cache_untouched_when_a_channel_write_fails_mid_session() = runTest {
        val radioController = FakeRadioController().apply { failChannelWriteAfter = 2 }
        val radioConfigRepository = FakeRadioConfigRepository()
        val oldSettings =
            listOf(
                ChannelSettings.Builder().also { wb -> wb.name = "Old Primary" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "Old Secondary" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "Old Tertiary" }.build(),
            )
        val importedSettings =
            listOf(
                ChannelSettings.Builder().also { wb -> wb.name = "Imported" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "Private" }.build(),
            )
        radioConfigRepository.setChannelSet(ChannelSet.Builder().also { wb -> wb.settings = oldSettings }.build())

        // A write failing inside the editLocalSettings session propagates out before the post-session cache
        // replace, so the local cache stays exactly as it was — nothing partially applied.
        assertFailsWith<IllegalStateException> {
            importChannelSet(
                channelSet = ChannelSet.Builder().also { wb -> wb.settings = importedSettings }.build(),
                radioController = radioController,
                radioConfigRepository = radioConfigRepository,
            )
        }

        assertEquals(oldSettings, radioConfigRepository.currentChannelSet.settings)
    }

    @Test
    fun import_rejects_imported_settings_beyond_slot_count_before_writing() = runTest {
        val radioController = FakeRadioController()
        val radioConfigRepository = FakeRadioConfigRepository()
        val oldSettings = listOf(ChannelSettings.Builder().also { wb -> wb.name = "Old" }.build())
        val oversizedSettings =
            (0..8).map { index -> ChannelSettings.Builder().also { wb -> wb.name = "Imported $index" }.build() }
        radioConfigRepository.setChannelSet(ChannelSet.Builder().also { wb -> wb.settings = oldSettings }.build())

        assertFailsWith<IllegalArgumentException> {
            importChannelSet(
                channelSet = ChannelSet.Builder().also { wb -> wb.settings = oversizedSettings }.build(),
                radioController = radioController,
                radioConfigRepository = radioConfigRepository,
            )
        }

        assertTrue(radioController.localChannels.isEmpty())
        assertEquals(oldSettings, radioConfigRepository.currentChannelSet.settings)
    }

    @Test
    fun replacement_apply_ignores_cached_settings_beyond_slot_count() = runTest {
        val radioController = FakeRadioController()
        val radioConfigRepository = FakeRadioConfigRepository()
        val oldSettings =
            (0..9).map { index -> ChannelSettings.Builder().also { wb -> wb.name = "Old $index" }.build() }
        val importedSettings = listOf(ChannelSettings.Builder().also { wb -> wb.name = "Imported" }.build())
        radioConfigRepository.setChannelSet(ChannelSet.Builder().also { wb -> wb.settings = oldSettings }.build())

        importChannelSet(
            channelSet = ChannelSet.Builder().also { wb -> wb.settings = importedSettings }.build(),
            radioController = radioController,
            radioConfigRepository = radioConfigRepository,
        )

        assertEquals((0..7).toList(), radioController.localChannels.map { it.index })
        assertEquals(importedSettings, radioConfigRepository.currentChannelSet.settings)
    }

    @Test
    fun replacement_apply_normalizes_oversized_raw_import_under_limit_before_writing() = runTest {
        val radioController = FakeRadioController()
        val radioConfigRepository = FakeRadioConfigRepository()
        radioConfigRepository.setChannelSet(
            ChannelSet.Builder()
                .also { wb -> wb.settings = listOf(ChannelSettings.Builder().also { wb -> wb.name = "Old" }.build()) }
                .build(),
        )
        // 9 raw entries: 7 unique valid secondaries + 2 blank placeholders -> normalizes to 7 (under the 8-slot limit).
        val ch0 =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Ch0"
                    wb.psk = byteArrayOf(1).toByteString()
                }
                .build()
        val ch1 =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Ch1"
                    wb.psk = byteArrayOf(2).toByteString()
                }
                .build()
        val ch2 =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Ch2"
                    wb.psk = byteArrayOf(3).toByteString()
                }
                .build()
        val ch3 =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Ch3"
                    wb.psk = byteArrayOf(4).toByteString()
                }
                .build()
        val ch4 =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Ch4"
                    wb.psk = byteArrayOf(5).toByteString()
                }
                .build()
        val ch5 =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Ch5"
                    wb.psk = byteArrayOf(6).toByteString()
                }
                .build()
        val ch6 =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Ch6"
                    wb.psk = byteArrayOf(7).toByteString()
                }
                .build()
        val raw =
            listOf(
                ch0,
                ch1,
                ChannelSettings.Builder().build(),
                ch2,
                ch3,
                ChannelSettings.Builder().build(),
                ch4,
                ch5,
                ch6,
            )

        importChannelSet(
            channelSet = ChannelSet.Builder().also { wb -> wb.settings = raw }.build(),
            radioController = radioController,
            radioConfigRepository = radioConfigRepository,
        )

        // Cache holds the normalized 7-entry set, not the raw 9-entry import.
        assertEquals(listOf(ch0, ch1, ch2, ch3, ch4, ch5, ch6), radioConfigRepository.currentChannelSet.settings)
    }

    @Test
    fun replacement_apply_rejects_settings_still_oversized_after_normalization_drops_placeholders() = runTest {
        val radioController = FakeRadioController()
        val radioConfigRepository = FakeRadioConfigRepository()
        radioConfigRepository.setChannelSet(
            ChannelSet.Builder()
                .also { wb -> wb.settings = listOf(ChannelSettings.Builder().also { wb -> wb.name = "Old" }.build()) }
                .build(),
        )
        // 10 raw entries: 9 genuinely unique + 1 blank placeholder. Normalization drops the blank
        // (-> 9) but the result still exceeds the 8-slot limit, so the post-normalize bounds check
        // must reject before any write or cache mutation.
        val unique =
            (1..9).map {
                ChannelSettings.Builder()
                    .also { wb ->
                        wb.name = "Ch$it"
                        wb.psk = byteArrayOf(it.toByte(), 0).toByteString()
                    }
                    .build()
            }

        assertFailsWith<IllegalArgumentException> {
            importChannelSet(
                channelSet =
                ChannelSet.Builder()
                    .also { wb -> wb.settings = unique + ChannelSettings.Builder().build() }
                    .build(),
                radioController = radioController,
                radioConfigRepository = radioConfigRepository,
            )
        }

        assertTrue(radioController.localChannels.isEmpty())
    }

    @Test
    fun replacement_apply_uses_current_local_lora_preset_when_imported_lora_is_absent() = runTest {
        val radioController = FakeRadioController()
        val radioConfigRepository = FakeRadioConfigRepository()
        // Device is on MEDIUM_FAST. The import omits lora_config, so identity resolution must fall
        // back to the device's current preset to detect this duplicate.
        radioConfigRepository.setLocalConfigDirect(
            LocalConfig.Builder()
                .also { wb ->
                    wb.lora =
                        Config.LoRaConfig.Builder()
                            .also { wb ->
                                wb.use_preset = true
                                wb.modem_preset = Config.LoRaConfig.ModemPreset.MEDIUM_FAST
                            }
                            .build()
                }
                .build(),
        )
        // Primary carries an explicit preset name; the secondary has an empty name that resolves to
        // the preset display name. Under MEDIUM_FAST the secondary resolves to "MediumFast" and
        // duplicates the primary. Under a default/LongFast fallback it would resolve to "LongFast"
        // and survive — so asserting the secondary is dropped proves the current-local preset was
        // used for identity (a regression to Config.LoRaConfig() would fail this test).
        val psk = byteArrayOf(1, 2, 3).toByteString()
        val primary =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "MediumFast"
                    wb.psk = psk
                }
                .build()
        val unnamedSecondary = ChannelSettings.Builder().also { wb -> wb.psk = psk }.build()

        importChannelSet(
            channelSet =
            ChannelSet.Builder()
                .also { wb -> wb.settings = listOf(primary, unnamedSecondary) }
                .build(), // no lora_config
            radioController = radioController,
            radioConfigRepository = radioConfigRepository,
        )

        // Secondary dropped as a semantic duplicate of the primary under the device's MEDIUM_FAST preset.
        assertEquals(listOf(primary), radioConfigRepository.currentChannelSet.settings)
    }

    @Test
    fun imported_lora_config_is_written_inside_the_same_transaction_when_it_differs() = runTest {
        val radioController = FakeRadioController()
        val radioConfigRepository = FakeRadioConfigRepository()
        val imported = Config.LoRaConfig.Builder().also { wb -> wb.region = Config.LoRaConfig.RegionCode.US }.build()
        radioConfigRepository.setLocalConfigDirect(
            LocalConfig.Builder()
                .also { wb ->
                    wb.lora =
                        Config.LoRaConfig.Builder()
                            .also { wb -> wb.region = Config.LoRaConfig.RegionCode.EU_868 }
                            .build()
                }
                .build(),
        )
        radioConfigRepository.setChannelSet(
            ChannelSet.Builder()
                .also { wb -> wb.settings = listOf(ChannelSettings.Builder().also { wb -> wb.name = "Old" }.build()) }
                .build(),
        )

        importChannelSet(
            channelSet =
            ChannelSet.Builder()
                .also { wb ->
                    wb.settings = listOf(ChannelSettings.Builder().also { wb -> wb.name = "Imported" }.build())
                    wb.lora_config = imported
                }
                .build(),
            radioController = radioController,
            radioConfigRepository = radioConfigRepository,
        )

        // LoRa write is the last op in the edit session, with no settle delays around it.
        assertEquals(listOf(Config.Builder().also { wb -> wb.lora = imported }.build()), radioController.localConfigs)
        assertEquals(imported, radioConfigRepository.currentChannelSet.lora_config)
    }

    @Test
    fun imported_lora_config_is_not_written_when_absent_or_unchanged() = runTest {
        val current = Config.LoRaConfig.Builder().also { wb -> wb.region = Config.LoRaConfig.RegionCode.US }.build()

        val absent = FakeRadioController()
        val absentRepo = FakeRadioConfigRepository()
        absentRepo.setLocalConfigDirect(LocalConfig.Builder().also { wb -> wb.lora = current }.build())
        absentRepo.setChannelSet(
            ChannelSet.Builder()
                .also { wb -> wb.settings = listOf(ChannelSettings.Builder().also { wb -> wb.name = "Old" }.build()) }
                .build(),
        )
        importChannelSet(
            channelSet =
            ChannelSet.Builder()
                .also { wb ->
                    wb.settings = listOf(ChannelSettings.Builder().also { wb -> wb.name = "Imported" }.build())
                }
                .build(), // no lora_config
            radioController = absent,
            radioConfigRepository = absentRepo,
        )

        val unchanged = FakeRadioController()
        val unchangedRepo = FakeRadioConfigRepository()
        unchangedRepo.setLocalConfigDirect(LocalConfig.Builder().also { wb -> wb.lora = current }.build())
        unchangedRepo.setChannelSet(
            ChannelSet.Builder()
                .also { wb -> wb.settings = listOf(ChannelSettings.Builder().also { wb -> wb.name = "Old" }.build()) }
                .build(),
        )
        importChannelSet(
            channelSet =
            ChannelSet.Builder()
                .also { wb ->
                    wb.settings = listOf(ChannelSettings.Builder().also { wb -> wb.name = "Imported" }.build())
                    wb.lora_config = current
                }
                .build(),
            radioController = unchanged,
            radioConfigRepository = unchangedRepo,
        )

        assertTrue(absent.localConfigs.isEmpty())
        assertTrue(unchanged.localConfigs.isEmpty())
    }

    // --- getChannelPreviewForAdd tests ---

    @Test
    fun preview_existing_channels_are_always_shown_and_selected() {
        val existing =
            listOf(
                ChannelSettings.Builder().also { wb -> wb.name = "A" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "B" }.build(),
            )

        val preview = getChannelPreviewForAdd(existing, emptyList(), ModelChannel.default.loraConfig, maxChannels = 8)

        assertEquals(existing, preview.settings)
        assertTrue(preview.selections.all { it })
    }

    @Test
    fun preview_unique_incoming_channels_are_shown_and_selected() {
        val incoming =
            listOf(
                ChannelSettings.Builder().also { wb -> wb.name = "C" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "D" }.build(),
            )

        val preview = getChannelPreviewForAdd(emptyList(), incoming, ModelChannel.default.loraConfig, maxChannels = 8)

        assertEquals(incoming, preview.settings)
        assertTrue(preview.selections.all { it })
    }

    @Test
    fun preview_incoming_duplicate_of_existing_is_omitted() {
        val channel =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Test"
                    wb.psk = byteArrayOf(1).toByteString()
                }
                .build()

        val preview =
            getChannelPreviewForAdd(listOf(channel), listOf(channel), ModelChannel.default.loraConfig, maxChannels = 8)

        assertEquals(listOf(channel), preview.settings)
    }

    @Test
    fun preview_duplicate_inside_incoming_keeps_first_and_omits_later() {
        val a =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "A"
                    wb.psk = byteArrayOf(1).toByteString()
                }
                .build()
        val b =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "B"
                    wb.psk = byteArrayOf(2).toByteString()
                }
                .build()

        val preview =
            getChannelPreviewForAdd(emptyList(), listOf(a, a, b), ModelChannel.default.loraConfig, maxChannels = 8)

        assertEquals(listOf(a, b), preview.settings)
        assertTrue(preview.selections[0])
        assertTrue(preview.selections[1])
    }

    @Test
    fun preview_same_name_different_psk_remains_visible() {
        val existingChan =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "A"
                    wb.psk = byteArrayOf(1).toByteString()
                }
                .build()
        val incomingChan =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "A"
                    wb.psk = byteArrayOf(2).toByteString()
                }
                .build()

        val preview =
            getChannelPreviewForAdd(
                listOf(existingChan),
                listOf(incomingChan),
                ModelChannel.default.loraConfig,
                maxChannels = 8,
            )

        assertEquals(2, preview.settings.size)
        assertTrue(preview.selections[1])
    }

    @Test
    fun preview_same_psk_different_name_remains_visible() {
        val psk = byteArrayOf(1, 2).toByteString()
        val existingChan =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "A"
                    wb.psk = psk
                }
                .build()
        val incomingChan =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "B"
                    wb.psk = psk
                }
                .build()

        val preview =
            getChannelPreviewForAdd(
                listOf(existingChan),
                listOf(incomingChan),
                ModelChannel.default.loraConfig,
                maxChannels = 8,
            )

        assertEquals(2, preview.settings.size)
        assertTrue(preview.selections[1])
    }

    @Test
    fun preview_empty_name_default_matches_explicit_preset_name_and_is_omitted() {
        val loraConfig = ModelChannel.default.loraConfig
        val existingChan =
            ChannelSettings.Builder()
                .also { wb -> wb.psk = byteArrayOf(1).toByteString() }
                .build() // resolves to "LongFast"
        val incomingChan =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "LongFast"
                    wb.psk = byteArrayOf(1).toByteString()
                }
                .build()

        val preview = getChannelPreviewForAdd(listOf(existingChan), listOf(incomingChan), loraConfig, maxChannels = 8)

        assertEquals(listOf(existingChan), preview.settings)
    }

    @Test
    fun preview_explicit_preset_name_matches_empty_name_default_and_is_omitted() {
        val loraConfig = ModelChannel.default.loraConfig
        val existingChan =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "LongFast"
                    wb.psk = byteArrayOf(1).toByteString()
                }
                .build()
        val incomingChan =
            ChannelSettings.Builder()
                .also { wb -> wb.psk = byteArrayOf(1).toByteString() }
                .build() // resolves to "LongFast"

        val preview = getChannelPreviewForAdd(listOf(existingChan), listOf(incomingChan), loraConfig, maxChannels = 8)

        assertEquals(listOf(existingChan), preview.settings)
    }

    @Test
    fun preview_psk_marker_matches_expanded_default_key_and_is_omitted() {
        val loraConfig = ModelChannel.default.loraConfig
        val expandedPsk =
            ModelChannel(
                settings = ChannelSettings.Builder().also { wb -> wb.psk = byteArrayOf(1).toByteString() }.build(),
                loraConfig = loraConfig,
            )
                .psk
        val markerChan =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Test"
                    wb.psk = byteArrayOf(1).toByteString()
                }
                .build()
        val expandedChan =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Test"
                    wb.psk = expandedPsk
                }
                .build()

        val preview = getChannelPreviewForAdd(listOf(markerChan), listOf(expandedChan), loraConfig, maxChannels = 8)

        assertEquals(listOf(markerChan), preview.settings)
    }

    @Test
    fun preview_non_long_fast_preset_default_duplicate_is_omitted() {
        val loraConfig =
            Config.LoRaConfig.Builder()
                .also { wb ->
                    wb.use_preset = true
                    wb.modem_preset = Config.LoRaConfig.ModemPreset.MEDIUM_FAST
                }
                .build()
        val existingChan =
            ChannelSettings.Builder()
                .also { wb -> wb.psk = byteArrayOf(1).toByteString() }
                .build() // resolves to "MediumFast"
        val incomingChan =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "MediumFast"
                    wb.psk = byteArrayOf(1).toByteString()
                }
                .build()

        val preview = getChannelPreviewForAdd(listOf(existingChan), listOf(incomingChan), loraConfig, maxChannels = 8)

        assertEquals(listOf(existingChan), preview.settings)
    }

    @Test
    fun preview_omitted_duplicates_do_not_consume_capacity() {
        val existing =
            listOf(
                ChannelSettings.Builder().also { wb -> wb.name = "A" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "B" }.build(),
            )
        val dup = ChannelSettings.Builder().also { wb -> wb.name = "A" }.build()
        val unique =
            listOf(
                ChannelSettings.Builder().also { wb -> wb.name = "C" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "D" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "E" }.build(),
            )

        val preview =
            getChannelPreviewForAdd(existing, listOf(dup) + unique, ModelChannel.default.loraConfig, maxChannels = 5)

        // Duplicate A omitted entirely (not in settings); C, D, E all selected because the omitted dup did not consume
        // capacity.
        assertEquals(listOf("A", "B", "C", "D", "E"), preview.settings.map { it.name })
        assertTrue(preview.selections[2]) // C
        assertTrue(preview.selections[3]) // D
        assertTrue(preview.selections[4]) // E
    }

    @Test
    fun preview_over_capacity_unique_incoming_remains_visible_but_unchecked() {
        val existing =
            listOf(
                ChannelSettings.Builder().also { wb -> wb.name = "A" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "B" }.build(),
            )
        val incoming =
            listOf(
                ChannelSettings.Builder().also { wb -> wb.name = "C" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "D" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "E" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "F" }.build(),
            )

        val preview = getChannelPreviewForAdd(existing, incoming, ModelChannel.default.loraConfig, maxChannels = 4)

        // All unique incoming are visible; C and D fit (4 total), E and F over capacity remain visible but unchecked.
        assertEquals(6, preview.settings.size)
        assertTrue(preview.selections[2]) // C fits
        assertTrue(preview.selections[3]) // D fits
        assertFalse(preview.selections[4]) // E over capacity
        assertFalse(preview.selections[5]) // F over capacity
    }

    @Test
    fun preview_existing_at_max_omits_nothing_but_all_incoming_unchecked() {
        val existing = (1..8).map { ChannelSettings.Builder().also { wb -> wb.name = "Ch$it" }.build() }
        val incoming = listOf(ChannelSettings.Builder().also { wb -> wb.name = "New" }.build())

        val preview = getChannelPreviewForAdd(existing, incoming, ModelChannel.default.loraConfig, maxChannels = 8)

        // Unique incoming still visible (not a duplicate) but unchecked because capacity is full.
        assertEquals(9, preview.settings.size)
        assertFalse(preview.selections[8])
    }

    @Test
    fun preview_settings_and_selections_are_always_size_matched() {
        val existing =
            listOf(
                ChannelSettings.Builder().also { wb -> wb.name = "A" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "B" }.build(),
            )
        val incoming =
            listOf(
                ChannelSettings.Builder()
                    .also { wb ->
                        wb.name = "A"
                        wb.psk = byteArrayOf(1).toByteString()
                    }
                    .build(), // duplicate of existing
                ChannelSettings.Builder().also { wb -> wb.name = "C" }.build(),
                ChannelSettings.Builder().also { wb -> wb.name = "D" }.build(),
            )

        val preview = getChannelPreviewForAdd(existing, incoming, ModelChannel.default.loraConfig, maxChannels = 8)

        assertEquals(preview.settings.size, preview.selections.size)
    }

    @Test
    fun preview_both_empty_produces_empty_preview() {
        val preview =
            getChannelPreviewForAdd(emptyList(), emptyList(), ModelChannel.default.loraConfig, maxChannels = 8)

        assertTrue(preview.settings.isEmpty())
        assertTrue(preview.selections.isEmpty())
    }

    // --- normalizeReplacementSettings tests ---

    @Test
    fun normalize_empty_list_passes_through() {
        assertEquals(emptyList(), normalizeReplacementSettings(emptyList(), ModelChannel.default.loraConfig))
    }

    @Test
    fun normalize_single_element_passes_through() {
        val primary =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Solo"
                    wb.psk = byteArrayOf(1).toByteString()
                }
                .build()

        assertEquals(listOf(primary), normalizeReplacementSettings(listOf(primary), ModelChannel.default.loraConfig))
    }

    @Test
    fun normalize_drops_blank_placeholder_secondary() {
        val primary =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Main"
                    wb.psk = byteArrayOf(1, 2).toByteString()
                }
                .build()
        val real =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Chat"
                    wb.psk = byteArrayOf(3).toByteString()
                }
                .build()

        val result =
            normalizeReplacementSettings(
                listOf(primary, ChannelSettings.Builder().build(), real),
                ModelChannel.default.loraConfig,
            )

        assertEquals(listOf(primary, real), result)
    }

    @Test
    fun normalize_preserves_blank_primary() {
        val blankPrimary = ChannelSettings.Builder().build()
        val real =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Chat"
                    wb.psk = byteArrayOf(3).toByteString()
                }
                .build()

        // Slot 0 is always preserved, even when blank (deliberate disable signal).
        val result = normalizeReplacementSettings(listOf(blankPrimary, real), ModelChannel.default.loraConfig)

        assertEquals(2, result.size)
        assertEquals(blankPrimary, result[0])
        assertEquals(real, result[1])
    }

    @Test
    fun normalize_blank_primary_does_not_seed_duplicate_tracking() {
        val blankPrimary = ChannelSettings.Builder().build()
        val publicSecondary = ChannelSettings.Builder().also { wb -> wb.psk = byteArrayOf(1).toByteString() }.build()

        val result =
            normalizeReplacementSettings(listOf(blankPrimary, publicSecondary), ModelChannel.default.loraConfig)

        assertEquals(listOf(blankPrimary, publicSecondary), result)
    }

    @Test
    fun normalize_drops_semantic_duplicate_secondary() {
        val primary =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Main"
                    wb.psk = byteArrayOf(1).toByteString()
                }
                .build()
        val dup =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Main"
                    wb.psk = byteArrayOf(1).toByteString()
                }
                .build()

        val result = normalizeReplacementSettings(listOf(primary, dup), ModelChannel.default.loraConfig)

        assertEquals(listOf(primary), result)
    }

    @Test
    fun normalize_keeps_same_name_different_psk() {
        val primary =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "A"
                    wb.psk = byteArrayOf(1).toByteString()
                }
                .build()
        val other =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "A"
                    wb.psk = byteArrayOf(2).toByteString()
                }
                .build()

        val result = normalizeReplacementSettings(listOf(primary, other), ModelChannel.default.loraConfig)

        assertEquals(listOf(primary, other), result)
    }

    @Test
    fun normalize_keeps_same_psk_different_name() {
        val psk = byteArrayOf(1, 2).toByteString()
        val primary =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "A"
                    wb.psk = psk
                }
                .build()
        val other =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "B"
                    wb.psk = psk
                }
                .build()

        val result = normalizeReplacementSettings(listOf(primary, other), ModelChannel.default.loraConfig)

        assertEquals(listOf(primary, other), result)
    }

    @Test
    fun normalize_compacts_valid_secondaries_into_sequential_slots() {
        val primary =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Main"
                    wb.psk = byteArrayOf(1).toByteString()
                }
                .build()
        val b =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "B"
                    wb.psk = byteArrayOf(2).toByteString()
                }
                .build()
        val c =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "C"
                    wb.psk = byteArrayOf(3).toByteString()
                }
                .build()

        // blank + duplicate mixed in; valid B and C must compact to slots 1 and 2 with no gap
        val result =
            normalizeReplacementSettings(
                listOf(
                    primary,
                    ChannelSettings.Builder().build(),
                    b,
                    ChannelSettings.Builder()
                        .also { wb ->
                            wb.name = "Main"
                            wb.psk = byteArrayOf(1).toByteString()
                        }
                        .build(),
                    c,
                ),
                ModelChannel.default.loraConfig,
            )

        assertEquals(listOf(primary, b, c), result)
    }

    @Test
    fun normalize_null_lora_falls_back_to_defaults_without_crashing() {
        val primary =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "Main"
                    wb.psk = byteArrayOf(1).toByteString()
                }
                .build()

        val result = normalizeReplacementSettings(listOf(primary, ChannelSettings.Builder().build()), loraConfig = null)

        assertEquals(listOf(primary), result)
    }

    @Test
    fun normalize_all_blank_input_preserves_only_primary() {
        // Primary is always preserved (even blank); both blank placeholder secondaries are dropped.
        val result =
            normalizeReplacementSettings(
                listOf(
                    ChannelSettings.Builder().build(),
                    ChannelSettings.Builder().build(),
                    ChannelSettings.Builder().build(),
                ),
                ModelChannel.default.loraConfig,
            )

        assertEquals(1, result.size)
    }
}
