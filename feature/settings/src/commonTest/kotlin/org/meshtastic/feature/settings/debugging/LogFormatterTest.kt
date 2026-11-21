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
package org.meshtastic.feature.settings.debugging

import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.BluetoothConnectionStatus
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceUIConfig
import org.meshtastic.proto.KeyVerificationAdmin
import org.meshtastic.proto.KeyVerificationNumberInform
import org.meshtastic.proto.LockdownAuth
import org.meshtastic.proto.ModuleConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogFormatterTest {

    /** A 32-byte channel PSK as it appears in hex inside a serialised AdminMessage. */
    private val pskHex = "c9ee13385d82a7ccf1163b6085aacff4193e6388add2f71c41668bb0d5fa1f44"

    private fun log(logMessage: String, decodedPayload: String? = null) = DebugViewModel.UiMeshLog(
        uuid = "1",
        messageType = "Admin",
        formattedReceivedDate = "2026-03-25",
        logMessage = logMessage,
        decodedPayload = decodedPayload,
    )

    private fun export(vararg logs: DebugViewModel.UiMeshLog): String =
        StringBuilder().also { formatLogsTo(it, logs.toList()) }.toString()

    @Test
    fun `export suppresses payload hex in okio's truncated form`() {
        // okio renders a ByteString longer than 64 bytes as `[size=N hex=<first 64 bytes>…]`, NOT `[hex=..]`. A real
        // set_channel / get_channel_response AdminMessage exceeds 64 bytes and the PSK lands inside that prefix, so
        // this is the shape that actually carries the key on the export path.
        val result = export(log("Data{portnum=ADMIN_APP, payload=[size=75 hex=1a3f080112391220$pskHex…]}"))

        assertFalse(result.contains(pskHex), "the channel key must not survive export in the truncated form")
        assertTrue(result.contains("payload=<suppressed>"))
        assertTrue(result.contains("portnum=ADMIN_APP"), "surrounding structure is kept")
    }

    @Test
    fun `export suppresses payload hex regardless of the byte count`() {
        // Guard the boundary between okio's two binary renderings rather than one example length.
        listOf(
            "payload=[hex=$pskHex]",
            "payload=[size=32 hex=$pskHex]",
            "payload=[size=140 hex=$pskHex…]",
            "payload=[size=1024 hex=$pskHex…]",
        )
            .forEach { field ->
                val result = export(log("Data{portnum=ADMIN_APP, $field, want_response=true}"))
                assertFalse(result.contains(pskHex), "key survived export for '$field'")
                assertTrue(result.contains("want_response=true"), "later fields preserved for '$field'")
            }
    }

    @Test
    fun `the per-entry copy action is sanitised like the export`() {
        // The copy button sits next to the export and its output gets pasted into the same public issue trackers, so it
        // must not be a way around the redaction. Binds the real code path, not just the helper.
        val copied =
            formatLogEntryForCopy(
                log(
                    logMessage = "Data{portnum=ADMIN_APP, payload=[size=75 hex=1a3f080112391220$pskHex…]}",
                    decodedPayload = "settings=ChannelSettings{psk=[hex=$pskHex], name=LongFast}",
                ),
            )

        assertFalse(copied.contains(pskHex), "the channel key must not reach the clipboard")
        assertTrue(copied.contains("payload=<suppressed>"))
        assertTrue(copied.contains("psk=<redacted>"))
        assertTrue(copied.contains("name=LongFast"), "non-sensitive fields are kept")
    }

    @Test
    fun `structured psk redaction covers okio's long and short forms`() {
        listOf("psk=[hex=$pskHex]", "psk=[size=140 hex=$pskHex…]").forEach { field ->
            val result = export(log("AdminMessage", decodedPayload = "settings=ChannelSettings{$field, name=X}"))
            assertFalse(result.contains(pskHex), "key survived for '$field'")
            assertTrue(result.contains("name=X"))
        }
    }

    @Test
    fun `formatLogsTo formats and redacts correctly`() {
        val logs =
            listOf(
                DebugViewModel.UiMeshLog(
                    uuid = "1",
                    messageType = "Packet",
                    formattedReceivedDate = "2026-03-25",
                    logMessage = "Hello",
                    decodedPayload = "session_passkey: secret\nother: value",
                ),
            )
        val out = StringBuilder()
        formatLogsTo(out, logs)

        val result = out.toString()
        assertTrue(result.contains("2026-03-25 [Packet]"))
        assertTrue(result.contains("Hello"))
        assertTrue(result.contains("session_passkey:<redacted>"))
        assertTrue(result.contains("other: value"))
    }

    @Test
    fun `export redacts the real proto equals-and-hex payload format`() {
        // Matches what the decoded payload actually looks like (proto toString uses '=' and '[hex=..]').
        val logs =
            listOf(
                DebugViewModel.UiMeshLog(
                    uuid = "1",
                    messageType = "Admin",
                    formattedReceivedDate = "2026-03-25",
                    logMessage = "AdminMessage",
                    decodedPayload = "session_passkey=[hex=dd8042fa0cfd7d17], region=US",
                ),
            )
        val out = StringBuilder()
        formatLogsTo(out, logs)

        val result = out.toString()
        assertFalse(result.contains("dd8042fa0cfd7d17"), "the hex secret must not survive export")
        assertTrue(result.contains("session_passkey=<redacted>"))
        assertTrue(result.contains("region=US"), "non-sensitive fields are kept")
    }

    @Test
    fun `appendLogcat redacts sensitive keys and keeps other lines`() {
        val out = StringBuilder()
        appendLogcat(out, "I/foo: hello world\nD/bar: private_key: deadbeef")

        val result = out.toString()
        assertTrue(result.contains("App Logcat"))
        assertTrue(result.contains("I/foo: hello world"))
        assertTrue(result.contains("private_key:<redacted>"))
    }

    @Test
    fun `redactText redacts keys across lines and keeps the rest`() {
        val result = redactText("D/x: session_passkey=[hex=dd8042fa0cfd7d17]\nD/x: region=US\nD/x: private_key: bb")

        assertFalse(result.contains("dd8042fa0cfd7d17"), "hex secret must not survive")
        assertFalse(result.contains(": bb"), "bare-token secret must not survive")
        assertTrue(result.contains("session_passkey=<redacted>"))
        assertTrue(result.contains("private_key:<redacted>"))
        assertTrue(result.contains("region=US"))
    }

    private val privateKeyHex = "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0"
    private val adminKeyOneHex = "a1a2a3a4a5a6a7a8a1a2a3a4a5a6a7a8a1a2a3a4a5a6a7a8a1a2a3a4a5a6a7a8"
    private val adminKeyTwoHex = "b1b2b3b4b5b6b7b8b1b2b3b4b5b6b7b8b1b2b3b4b5b6b7b8b1b2b3b4b5b6b7b8"
    private val publicKeyHex = "c1c2c3c4c5c6c7c8c1c2c3c4c5c6c7c8c1c2c3c4c5c6c7c8c1c2c3c4c5c6c7c8"

    private fun rendered(message: Any): String = sanitizeForExport(message.toString())

    private fun security(block: (Config.SecurityConfig.Builder) -> Unit): Config =
        Config.Builder().also { it.security = Config.SecurityConfig.Builder().also(block).build() }.build()

    @Test
    fun `security config keys are redacted and the public key and neighbors survive`() {
        val result =
            rendered(
                security {
                    it.public_key = publicKeyHex.decodeHex()
                    it.private_key = privateKeyHex.decodeHex()
                    it.admin_key = listOf(adminKeyOneHex.decodeHex(), adminKeyTwoHex.decodeHex())
                    it.serial_enabled = true
                },
            )

        listOf(privateKeyHex, adminKeyOneHex, adminKeyTwoHex).forEach { assertFalse(result.contains(it), it) }
        assertTrue(result.contains(publicKeyHex), "public keys are not secret")
        assertTrue(result.contains("serial_enabled=true"), "the field after the redacted list is kept")
    }

    @Test
    fun `a string credential in the last field keeps the closing brace`() {
        // No generated message ends in a string credential, so this is Wire's `name=value` shape written by hand.
        val result = sanitizeForExport("NetworkConfig{wifi_ssid=home, wifi_psk=last field pw}")

        assertTrue(result.endsWith("wifi_ssid=home, wifi_psk=<redacted>}"), result)
    }

    @Test
    fun `a bytes list is redacted whole and the next field survives`() {
        val result =
            rendered(security { it.admin_key = listOf(adminKeyOneHex.decodeHex(), adminKeyTwoHex.decodeHex()) })

        assertFalse(result.contains(adminKeyOneHex))
        assertFalse(result.contains(adminKeyTwoHex))
        assertTrue(result.contains("admin_key=<redacted>, is_managed=false"), result)
    }

    @Test
    fun `wifi password with quotes and commas is redacted whole`() {
        val config =
            Config.Builder()
                .also {
                    it.network =
                        Config.NetworkConfig.Builder()
                            .also { n ->
                                n.wifi_ssid = "home"
                                n.wifi_psk = "hunter2 \"x\", y"
                                n.ntp_server = "pool.example"
                            }
                            .build()
                }
                .build()

        val result = rendered(config)

        listOf("hunter2", "\\, y").forEach { assertFalse(result.contains(it), it) }
        assertTrue(result.contains("wifi_ssid=home"))
        assertTrue(result.contains("ntp_server=pool.example"))
    }

    @Test
    fun `bluetooth fixed pin is redacted as the last field`() {
        val config =
            Config.Builder()
                .also {
                    it.bluetooth =
                        Config.BluetoothConfig.Builder()
                            .also { b ->
                                b.enabled = true
                                b.mode = Config.BluetoothConfig.PairingMode.FIXED_PIN
                                b.fixed_pin = 424242
                            }
                            .build()
                }
                .build()

        val result = rendered(config)

        assertFalse(result.contains("424242"))
        assertTrue(result.contains("mode=FIXED_PIN"))
        assertTrue(result.endsWith("fixed_pin=<redacted>}}"), result)
    }

    @Test
    fun `mqtt credentials are redacted and neighboring fields survive`() {
        val moduleConfig =
            ModuleConfig.Builder()
                .also {
                    it.mqtt =
                        ModuleConfig.MQTTConfig.Builder()
                            .also { m ->
                                m.address = "mqtt.example"
                                m.username = "mesh user"
                                m.password = "pw 9f3a, tail"
                                m.root = "msh/US"
                            }
                            .build()
                }
                .build()

        val result = rendered(moduleConfig)

        listOf("mesh user", "pw 9f3a", "tail").forEach { assertFalse(result.contains(it), it) }
        assertTrue(result.contains("address=mqtt.example"))
        assertTrue(result.contains("root=msh/US"))
    }

    @Test
    fun `admin message session passkey and lockdown passphrase are redacted`() {
        val admin =
            AdminMessage.Builder()
                .also {
                    it.session_passkey = "dd8042fa0cfd7d17".decodeHex()
                    it.lockdown_auth =
                        LockdownAuth.Builder()
                            .also { l ->
                                l.passphrase = "00112233445566778899aabbccddeeff".decodeHex()
                                l.boots_remaining = 3
                            }
                            .build()
                }
                .build()

        val result = rendered(admin)

        assertFalse(result.contains("dd8042fa0cfd7d17"))
        assertFalse(result.contains("00112233445566778899aabbccddeeff"))
        assertTrue(result.contains("boots_remaining=3"), "fields after a hex rendering are kept")
    }

    @Test
    fun `a text passphrase containing a bracket and comma leaves no part of the secret`() {
        val result =
            rendered(LockdownAuth.Builder().also { it.passphrase = "prefix], secret-tail".encodeUtf8() }.build())

        listOf("prefix", "secret-tail").forEach { assertFalse(result.contains(it), it) }
        assertTrue(result.contains("passphrase=<redacted>"))
    }

    @Test
    fun `a text passphrase containing a bracket and brace leaves no part of the secret`() {
        val result =
            rendered(LockdownAuth.Builder().also { it.passphrase = "prefix]}secret-tail".encodeUtf8() }.build())

        listOf("prefix", "secret-tail").forEach { assertFalse(result.contains(it), it) }
    }

    @Test
    fun `a text admin key list containing a bracket leaves no part of the secret`() {
        val result =
            rendered(
                security {
                    it.admin_key = listOf("first], secret-one".encodeUtf8(), "second]}secret-two".encodeUtf8())
                },
            )

        listOf("first", "secret-one", "second", "secret-two").forEach { assertFalse(result.contains(it), it) }
    }

    @Test
    fun `device ui pin and key verification numbers are redacted`() {
        val ui =
            rendered(
                DeviceUIConfig.Builder()
                    .also {
                        it.pin_code = 135790
                        it.screen_brightness = 77
                    }
                    .build(),
            )
        val verify =
            rendered(
                KeyVerificationAdmin.Builder()
                    .also {
                        it.remote_nodenum = 5
                        it.security_number = 864209
                    }
                    .build(),
            )
        val inform =
            rendered(
                ClientNotification.Builder()
                    .also {
                        it.key_verification_number_inform =
                            KeyVerificationNumberInform.Builder()
                                .also { k ->
                                    k.remote_longname = "Peer"
                                    k.security_number = 975310
                                }
                                .build()
                    }
                    .build(),
            )

        assertFalse(ui.contains("135790"))
        assertTrue(ui.contains("screen_brightness=77"))
        assertFalse(verify.contains("864209"))
        assertTrue(verify.contains("remote_nodenum=5"))
        assertFalse(inform.contains("975310"))
        assertTrue(inform.contains("remote_longname=Peer"))
    }

    @Test
    fun `bluetooth connection pin is redacted but gpio pin fields are not`() {
        val connection =
            rendered(
                BluetoothConnectionStatus.Builder()
                    .also {
                        it.pin = 246810
                        it.rssi = -50
                    }
                    .build(),
            )
        val gpio =
            rendered(
                ModuleConfig.DetectionSensorConfig.Builder()
                    .also {
                        it.monitor_pin = 17
                        it.enabled = true
                    }
                    .build(),
            )

        assertFalse(connection.contains("246810"))
        assertTrue(connection.contains("rssi=-50"))
        assertTrue(gpio.contains("monitor_pin=17"), "a field that only ends in pin is not a credential")
    }
}
